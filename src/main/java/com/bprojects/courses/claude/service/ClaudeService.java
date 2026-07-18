package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.tools.MyTools;
import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

    private static final String NO_CODE_FENCES =
            " When sharing code, output it directly without markdown code fences (no triple backticks).";

    private final ChatClient chatClient;
    private final ToolsService toolsService;
    // Present only when RAG is enabled. Attached per request (not as a default advisor) so a
    // request can skip it entirely — no vector-store query — when RAG is disabled for that call.
    @Nullable
    private final QuestionAnswerAdvisor ragAdvisor;

    // Inject the autoconfigured Builder. The QuestionAnswerAdvisor is only present
    // when RAG is enabled (rag.enabled=true) -> injected via ObjectProvider.
    public ClaudeService(ChatClient.Builder chatClientBuilder,
                         ToolsService toolsService,
                         ObjectProvider<QuestionAnswerAdvisor> qaAdvisorProvider) {
        this.toolsService = toolsService;
        this.ragAdvisor = qaAdvisorProvider.getIfAvailable();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        this.chatClient = chatClientBuilder
                .defaultSystem("You are a witty, helpful AI assistant." + NO_CODE_FENCES)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // Synchronous (blocking) call to Claude
    public String generateResponse(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Integer thinkingBudget,
            @Nullable Set<String> ragDocumentIds,
            @Nullable String conversationId,
            @Nullable Tone tone) {
        return generateResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone, null);
    }

    // Synchronous (blocking) call to Claude, optionally with attached images (vision)
    public String generateResponse(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Integer thinkingBudget,
            @Nullable Set<String> ragDocumentIds,
            @Nullable String conversationId,
            @Nullable Tone tone,
            @Nullable List<Media> media) {
        var spec = buildPromptSpec(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone, media);
        return spec.call().content();
    }

    // Streaming (reactive) call to Claude
    public Flux<String> streamResponse(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Integer thinkingBudget,
            @Nullable Set<String> ragDocumentIds,
            @Nullable String conversationId,
            @Nullable Tone tone) {
        return streamResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone, null);
    }

    // Streaming (reactive) call to Claude, optionally with attached images (vision)
    public Flux<String> streamResponse(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Integer thinkingBudget,
            @Nullable Set<String> ragDocumentIds,
            @Nullable String conversationId,
            @Nullable Tone tone,
            @Nullable List<Media> media) {
        var spec = buildPromptSpec(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone, media);
        return spec.stream().content();
    }

    private ChatClient.ChatClientRequestSpec buildPromptSpec(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Integer thinkingBudget,
            @Nullable Set<String> ragDocumentIds,
            @Nullable String conversationId,
            @Nullable Tone tone,
            @Nullable List<Media> media) {

        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }
        final String finalConversationId = Optional.ofNullable(conversationId).orElse(String.valueOf(UUID.randomUUID()));
        var spec = this.chatClient.prompt()
                .user(u -> {
                    u.text(message);
                    if (media != null) {
                        media.forEach(u::media);
                    }
                })
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId));

        spec = applyRag(spec, ragDocumentIds);

        List<MyTools> activeTools = toolsService.getEnabledTools();
        if (activeTools != null && !activeTools.isEmpty()) {
            spec = spec.tools(activeTools.toArray());
        }

        var optionsBuilder = AnthropicChatOptions.builder();
        // Enable Claude's built-in (server-side) web search tool unless disabled by property.
        if (toolsService.isNativeWebSearchToolEnabled()) {
            optionsBuilder.webSearchTool(AnthropicWebSearchTool.builder().maxUses(5).build());
        }
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
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
            }
        }
        spec = spec.options(optionsBuilder);
        if (tone != null) {
            spec = spec.system(getSystemPrompt(tone));
        }
        return spec;
    }

    /**
     * Attach the RAG retrieval advisor to the request, scoped by the caller's ragDocumentIds:
     *   - absent (null)          -> retrieve across all documents
     *   - present with valid ids -> restrict retrieval to those documents (docId filter)
     *   - present but empty      -> skip the advisor entirely: no vector-store query at all
     * Returns the spec unchanged when RAG is disabled (ragAdvisor is null).
     */
    private ChatClient.ChatClientRequestSpec applyRag(
            ChatClient.ChatClientRequestSpec spec,
            @Nullable Set<String> ragDocumentIds) {
        if (ragAdvisor == null) {
            return spec;
        }
        if (ragDocumentIds == null) {
            return spec.advisors(ragAdvisor);
        }
        String inList = ragDocumentIds.stream()
                .filter(id -> id != null && id.matches("[0-9a-fA-F-]{36}"))   // UUIDs only, block injection
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));
        if (inList.isBlank()) {
            return spec;   // empty selection -> advisor not attached -> RAG off, no DB query
        }
        // QuestionAnswerAdvisor re-parses FILTER_EXPRESSION as filter text -> pass a DSL string.
        String docFilter = "docId in [" + inList + "]";
        return spec.advisors(ragAdvisor)
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, docFilter));
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