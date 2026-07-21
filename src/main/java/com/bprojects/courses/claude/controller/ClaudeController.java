package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.dto.ChatRequestInputDto;
import com.bprojects.courses.claude.dto.ChatRequestWithMediaInputDto;
import com.bprojects.courses.claude.service.ClaudeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class ClaudeController {

    private final ClaudeService claudeService;

    public ClaudeController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    // Simple GET text generation
    @GetMapping("/chat")
    public String chat(ChatRequestInputDto request) {
        return claudeService.generateResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone());
    }

    // Server-Sent Events (SSE) for real-time text streaming
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(ChatRequestInputDto request) {
        return claudeService.streamResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone());
    }

    // Multipart POST variant: same params as GET plus optional file attachments (images + PDFs)
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String chatWithMedia(ChatRequestWithMediaInputDto request) {
        DocumentHelper.Attachments att = DocumentHelper.toAttachments(request.getMedia());
        return claudeService.generateResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithMedia(ChatRequestWithMediaInputDto request) {
        DocumentHelper.Attachments att = DocumentHelper.toAttachments(request.getMedia());
        return claudeService.streamResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

}
