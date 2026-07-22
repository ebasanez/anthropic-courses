package com.bprojects.courses.claude.dto;

import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Common chat request parameters shared by the {@code /chat} and {@code /stream} endpoints.
 * Bound by Spring as a command object from query params (GET) or form fields (multipart POST).
 */
public class ChatRequestInputDto {

    private String message;
    @Nullable
    private Integer maxTokens;
    @Nullable
    private Double temperature;
    @Nullable
    private Integer thinkingBudget;
    @Nullable
    private Set<String> ragDocumentIds;
    @Nullable
    private String conversationId;
    @Nullable
    private Tone tone;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Nullable
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(@Nullable Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Nullable
    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(@Nullable Double temperature) {
        this.temperature = temperature;
    }

    @Nullable
    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public void setThinkingBudget(@Nullable Integer thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    @Nullable
    public Set<String> getRagDocumentIds() {
        return ragDocumentIds;
    }

    public void setRagDocumentIds(@Nullable Set<String> ragDocumentIds) {
        this.ragDocumentIds = ragDocumentIds;
    }

    @Nullable
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(@Nullable String conversationId) {
        this.conversationId = conversationId;
    }

    @Nullable
    public Tone getTone() {
        return tone;
    }

    public void setTone(@Nullable Tone tone) {
        this.tone = tone;
    }
}
