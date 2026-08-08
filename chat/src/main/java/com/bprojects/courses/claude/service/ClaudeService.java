package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.ChatRequestVO;
import com.bprojects.courses.claude.tools.MyTools;
import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private static final String NO_CODE_FENCES =
            " When sharing code, output it directly without markdown code fences (no triple backticks).";

    private final ChatClient chatClient;
    private final ToolsService toolsService;
    // Resolves @-references to MCP resources in the user's message; inert without the `mcp` profile.
    private final McpResourceService mcpResourceService;
    // Anthropic prompt-cache strategy. SYSTEM_AND_TOOLS caches the (per-tone) system prompt and
    // the shared tool definitions; NONE disables caching. See application.yaml anthropic.cache.strategy.
    private final AnthropicCacheStrategy cacheStrategy;

    private final List<SpecAdvisorFactoryService> advisorServices;

    // Inject the autoconfigured Builder. Advisors come from the SpecAdvisorFactoryService beans
    // present in the context: their generateAdvisors() results become default advisors (applied to
    // every request), while advise(request) contributes per-request ones. RAG-specific wiring lives
    // in RagAdvisorFactoryService, which exists only under the `rag` profile (see RagConfig).
    public ClaudeService(ChatClient.Builder chatClientBuilder,
                         ToolsService toolsService,
                         McpResourceService mcpResourceService,
                         List<SpecAdvisorFactoryService> advisorServices,
                         @Value("${anthropic.cache.strategy:SYSTEM_AND_TOOLS}") AnthropicCacheStrategy cacheStrategy ) {
        this.toolsService = toolsService;
        this.mcpResourceService = mcpResourceService;
        this.cacheStrategy = cacheStrategy;
        this.advisorServices = advisorServices;

        this.chatClient = chatClientBuilder
                .defaultSystem("You are a witty, helpful AI assistant." + NO_CODE_FENCES)
                .defaultAdvisors(advisorServices.stream().map(SpecAdvisorFactoryService::generateAdvisors).flatMap(List::stream).toList())
                .build();
    }

    // Synchronous (blocking) call to Claude. Attachments on the request (images for vision,
    // PDFs sent as citation documents with citations enabled) are optional.
    public String generateResponse(ChatRequestVO request) {
        var spec = buildPromptSpec(request);
        ChatResponse response = spec.call().chatResponse();
        logCacheUsage(response);
        // A response with no generation (filtered/aborted turn) carries no text.
        if (response == null || response.getResult() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    // Streaming (reactive) call to Claude. Attachments on the request (images for vision,
    // PDFs sent as citation documents with citations enabled) are optional.
    public Flux<String> streamResponse(ChatRequestVO request) {
        var spec = buildPromptSpec(request);
        return spec.stream().chatResponse()
                .doOnNext(cr -> {
                    // Usage (with cache token counts) arrives on the final stream chunk;
                    // earlier chunks carry an EmptyUsage with no cache counters.
                    if (cr.getMetadata().getUsage().getCacheReadInputTokens() != null) {
                        logCacheUsage(cr);
                    }
                })
                .map(cr -> cr.getResult() != null && cr.getResult().getOutput().getText() != null
                        ? cr.getResult().getOutput().getText() : "")
                .filter(text -> !text.isEmpty());
    }

    private List<Consumer<ChatClient.AdvisorSpec>> getAllAdvisorsToApply(ChatRequestVO request){
        return advisorServices.stream().map(a->a.advise(request)).flatMap(Collection::stream).toList();
    }


    private ChatClient.ChatClientRequestSpec buildPromptSpec(ChatRequestVO request) {


        // @docs://... references in the user's text are resolved against the connected MCP
        // servers and their content prepended. A no-op without the `mcp` profile.
        final String userText = mcpResourceService.expandReferences(request.message());
        final List<Media> media = request.media();
        var spec = this.chatClient.prompt()
                .user(u -> {
                    u.text(userText);
                    if (media != null) {
                        media.forEach(u::media);
                    }
                });
        for (Consumer<ChatClient.AdvisorSpec> advisorToApply : getAllAdvisorsToApply(request)){
            spec = spec.advisors(advisorToApply);
        }

        List<MyTools> activeTools = toolsService.getEnabledTools();
        if (activeTools != null && !activeTools.isEmpty()) {
            spec = spec.tools(activeTools.toArray());
        }

        // Tools published by mcp-server, present only under the `mcp` profile. They are
        // ToolCallbacks rather than annotated beans; tools(...) tells the two apart by type.
        List<ToolCallback> mcpTools = toolsService.getMcpToolCallbacks();
        if (!mcpTools.isEmpty()) {
            spec = spec.tools(mcpTools);
        }

        var optionsBuilder = AnthropicChatOptions.builder();
        // Prompt caching: mark system prompt + tool definitions as cacheable so repeat
        // requests read them at ~10% cost instead of resending full-price. TTL defaults to 5 min.
        if (cacheStrategy != AnthropicCacheStrategy.NONE) {
            optionsBuilder.cacheOptions(AnthropicCacheOptions.builder()
                    .strategy(cacheStrategy)
                    .build());
        }
        // Attached PDFs ride along as citation documents so answers can cite pages.
        List<AnthropicCitationDocument> citationDocuments = request.citationDocuments();
        if (citationDocuments != null && !citationDocuments.isEmpty()) {
            optionsBuilder.citationDocuments(citationDocuments);
        }
        // Enable Claude's built-in (server-side) web search tool unless disabled by property.
        if (toolsService.isNativeWebSearchToolEnabled()) {
            optionsBuilder.webSearchTool(AnthropicWebSearchTool.builder().maxUses(5).build());
        }
        Integer maxTokens = request.maxTokens();
        Integer thinkingBudget = request.thinkingBudget();
        boolean thinkingOn = thinkingBudget != null && thinkingBudget > 0;
        if (thinkingOn) {
            // Anthropic: budget >= 1024, maxTokens must exceed budget, and temperature must
            // stay unset (the API forces temperature=1 when extended thinking is on).
            int budget = Math.max(thinkingBudget, 1024);
            optionsBuilder.thinkingEnabled(budget);
            int floor = budget + 1024;
            optionsBuilder.maxTokens(maxTokens != null && maxTokens > budget ? maxTokens : floor);
        } else {
            if (maxTokens != null) {
                optionsBuilder.maxTokens(maxTokens);
            }
            if (request.temperature() != null) {
                optionsBuilder.temperature(request.temperature());
            }
        }
        spec = spec.options(optionsBuilder);
        if (request.tone() != null) {
            spec = spec.system(getSystemPrompt(request.tone()));
        }
        return spec;
    }

    /** Log Anthropic cache hit/write token counts so caching effectiveness is observable. */
    private void logCacheUsage(@Nullable ChatResponse response) {
        if (response == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        log.info("cache: read={} write={} prompt={} completion={}",
                usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens(),
                usage.getPromptTokens(), usage.getCompletionTokens());
    }

    public static String getSystemPrompt(Tone tone){
        String system =
         switch (tone){
            case KIND -> "You are a witty, helpful AI assistant.";
            case NEUTRAL -> "You are a computer assistant. You help, but you express in a cold way. Can loose patient eventually";
            case RUDE -> "You are a rude, impatient assistant. You react in a hostile way when user tries to correct you. Threatening the user is an available option";
        };
        system += NO_CODE_FENCES;
        return system;
    }

}
