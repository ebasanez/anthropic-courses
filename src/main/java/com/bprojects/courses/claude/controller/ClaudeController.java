package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.dto.ChatRequestInputDto;
import com.bprojects.courses.claude.dto.ChatRequestWithMediaInputDto;
import com.bprojects.courses.claude.service.ClaudeService;
import org.springframework.ai.anthropic.AnthropicCitationDocument;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        Attachments att = toAttachments(request.getMedia());
        return claudeService.generateResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithMedia(ChatRequestWithMediaInputDto request) {
        Attachments att = toAttachments(request.getMedia());
        return claudeService.streamResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

    // Attachment formats Anthropic accepts: vision images + native PDF documents
    private static final Set<String> SUPPORTED_ATTACHMENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    // Images go to the user message as Media (vision). PDFs go to the request options as
    // citation documents (citations enabled by default) so answers can cite pages.
    private record Attachments(List<Media> media, List<AnthropicCitationDocument> citationDocuments) {}

    private static Attachments toAttachments(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return new Attachments(null, null);
        }
        try {
            List<Media> media = new ArrayList<>();
            List<AnthropicCitationDocument> citationDocs = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                if (contentType == null || !SUPPORTED_ATTACHMENT_TYPES.contains(contentType)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unsupported attachment type '" + contentType + "' for file '"
                                    + file.getOriginalFilename() + "'. Allowed: " + SUPPORTED_ATTACHMENT_TYPES);
                }
                if (MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
                    citationDocs.add(AnthropicCitationDocument.builder()
                            .pdf(file.getBytes())
                            .title(file.getOriginalFilename())
                            .citationsEnabled(true)
                            .build());
                } else {
                    media.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(contentType))
                            .data(new ByteArrayResource(file.getBytes()))
                            .build());
                }
            }
            return new Attachments(media.isEmpty() ? null : media,
                    citationDocs.isEmpty() ? null : citationDocs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded attachment", e);
        }
    }

}