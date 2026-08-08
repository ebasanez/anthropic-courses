package com.bprojects.courses.claude;

import com.bprojects.courses.claude.vo.Tone;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.anthropic.AnthropicCitationDocument;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Set;

public record ChatRequestVO(
        String message,
        Integer maxTokens,
        @Nullable Double temperature,
        @Nullable Integer thinkingBudget,
        @Nullable Set<String> ragDocumentIds,
        @Nullable String conversationId,
        @Nullable Tone tone,
        @Nullable List<Media> media,
        @Nullable List<AnthropicCitationDocument> citationDocuments) {

    /** Request without attachments: no images (vision) and no citation documents. */
    public ChatRequestVO(String message,
                         Integer maxTokens,
                         @Nullable Double temperature,
                         @Nullable Integer thinkingBudget,
                         @Nullable Set<String> ragDocumentIds,
                         @Nullable String conversationId,
                         @Nullable Tone tone) {
        this(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone, null, null);
    }

    /** Same request with the user text replaced — used when a prompt is rewritten before the call. */
    public ChatRequestVO withMessage(String message) {
        return new ChatRequestVO(message, maxTokens, temperature, thinkingBudget, ragDocumentIds,
                conversationId, tone, media, citationDocuments);
    }

    /** Same request with attachments added: images ride as media, PDFs as citation documents. */
    public ChatRequestVO withAttachments(@Nullable List<Media> media,
                                        @Nullable List<AnthropicCitationDocument> citationDocuments) {
        return new ChatRequestVO(message, maxTokens, temperature, thinkingBudget, ragDocumentIds,
                conversationId, tone, media, citationDocuments);
    }
}
