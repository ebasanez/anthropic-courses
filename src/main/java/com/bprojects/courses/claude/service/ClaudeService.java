package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ClaudeService {

    private final ChatClient chatClient;

    // Inject the autoconfigured Builder
    public ClaudeService(ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a witty, helpful AI assistant.")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // Synchronous (blocking) call to Claude
    public String generateResponse(
            String message,
            Integer maxTokens,
            @Nullable String conversationId,
            @Nullable Tone tone) {
        var spec = this.chatClient.prompt().user(message);
        if (conversationId != null) {
            spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }
        if (maxTokens != null) {
            spec = spec.options(AnthropicChatOptions.builder().maxTokens(maxTokens));
        }
        if (tone != null) {
            spec = spec.system(getSystemPrompt(tone));
        }
        return spec.call().content();
    }

    // Streaming (reactive) call to Claude
    public Flux<String> streamResponse(String message) {
        return this.chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    private String getSystemPrompt(Tone tone){
        return switch (tone){
            case KIND -> "You are a witty, helpful AI assistant.";
            case NEUTRAL -> "You are a computer assistant. You help, but you express in a cold way. Can loose patient eventually";
            case RUDE -> "You are a rude, impatient assistant. You react in a hostile way when user tries to correct you. Threatening the user is an available option";
        };
    }

}