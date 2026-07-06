package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.tools.MyTools;
import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaudeService {

    private static final String NO_CODE_FENCES =
            " When sharing code, output it directly without markdown code fences (no triple backticks).";

    private final ChatClient chatClient;
    private final ToolsService toolsService;

    // Inject the autoconfigured Builder
    public ClaudeService(ChatClient.Builder chatClientBuilder, ToolsService toolsService) {
        this.toolsService = toolsService;
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
            @Nullable String conversationId,
            @Nullable Tone tone) {
        var spec = buildPromptSpec(message, maxTokens, temperature, conversationId, tone);
        return spec.call().content();
    }

    // Streaming (reactive) call to Claude
    public Flux<String> streamResponse(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable String conversationId,
            @Nullable Tone tone) {
        var spec = buildPromptSpec(message, maxTokens, temperature, conversationId, tone);
        return spec.stream().content();
    }

    private ChatClient.ChatClientRequestSpec buildPromptSpec(
            String message,
            Integer maxTokens,
            @Nullable Double temperature,
            @Nullable String conversationId,
            @Nullable Tone tone) {

        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }
        final String finalConversationId = Optional.ofNullable(conversationId).orElse(String.valueOf(UUID.randomUUID()));
        var spec = this.chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId));

        List<MyTools> activeTools = toolsService.getEnabledTools();
        if (activeTools != null && !activeTools.isEmpty()) {
            spec = spec.tools(activeTools.toArray());
        }

        var optionsBuilder = AnthropicChatOptions.builder();
        // Enable Claude's built-in (server-side) web search tool unless disabled by property.
        if (toolsService.isNativeWebSearchToolEnabled()) {
            optionsBuilder.webSearchTool(AnthropicWebSearchTool.builder().maxUses(5).build());
        }
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        spec = spec.options(optionsBuilder);
        if (tone != null) {
            spec = spec.system(getSystemPrompt(tone));
        }
        return spec;
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