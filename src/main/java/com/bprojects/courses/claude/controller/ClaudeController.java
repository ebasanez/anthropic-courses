package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.service.ClaudeService;
import com.bprojects.courses.claude.vo.Tone;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String chat(@RequestParam(value = "message") String message,
                        @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                        @RequestParam(value = "temperature", required = false) Double temperature,
                        @RequestParam(value = "thinkingBudget", required = false) Integer thinkingBudget,
                        @RequestParam(value = "ragDocumentIds", required = false) Set<String> ragDocumentIds,
                        @RequestParam(value = "conversationId", required = false) String conversationId,
                       @RequestParam(value = "tone", required = false) Tone tone) {
        return claudeService.generateResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone);
    }

    // Server-Sent Events (SSE) for real-time text streaming
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(value = "message") String message,
                                @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                                @RequestParam(value = "temperature", required = false) Double temperature,
                                @RequestParam(value = "thinkingBudget", required = false) Integer thinkingBudget,
                                @RequestParam(value = "ragDocumentIds", required = false) Set<String> ragDocumentIds,
                                @RequestParam(value = "conversationId", required = false) String conversationId,
                                @RequestParam(value = "tone", required = false) Tone tone) {
        return claudeService.streamResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds, conversationId, tone);
    }

    // Multipart POST variant: same params as GET plus optional image attachments (vision)
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String chatWithImages(@RequestParam(value = "message") String message,
                                 @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                                 @RequestParam(value = "temperature", required = false) Double temperature,
                                 @RequestParam(value = "thinkingBudget", required = false) Integer thinkingBudget,
                                 @RequestParam(value = "ragDocumentIds", required = false) Set<String> ragDocumentIds,
                                 @RequestParam(value = "conversationId", required = false) String conversationId,
                                 @RequestParam(value = "tone", required = false) Tone tone,
                                 @RequestParam(value = "images", required = false) MultipartFile[] images) {
        return claudeService.generateResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds,
                conversationId, tone, toMedia(images));
    }

    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithImages(@RequestParam(value = "message") String message,
                                         @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                                         @RequestParam(value = "temperature", required = false) Double temperature,
                                         @RequestParam(value = "thinkingBudget", required = false) Integer thinkingBudget,
                                         @RequestParam(value = "ragDocumentIds", required = false) Set<String> ragDocumentIds,
                                         @RequestParam(value = "conversationId", required = false) String conversationId,
                                         @RequestParam(value = "tone", required = false) Tone tone,
                                         @RequestParam(value = "images", required = false) MultipartFile[] images) {
        return claudeService.streamResponse(message, maxTokens, temperature, thinkingBudget, ragDocumentIds,
                conversationId, tone, toMedia(images));
    }

    // Image formats Anthropic vision accepts
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private static List<Media> toMedia(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            return null;
        }
        try {
            List<Media> media = new ArrayList<>(images.length);
            for (MultipartFile image : images) {
                if (image.isEmpty()) {
                    continue;
                }
                String contentType = image.getContentType();
                if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unsupported image type '" + contentType + "' for file '"
                                    + image.getOriginalFilename() + "'. Allowed: " + SUPPORTED_IMAGE_TYPES);
                }
                media.add(Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(contentType))
                        .data(new ByteArrayResource(image.getBytes()))
                        .build());
            }
            return media.isEmpty() ? null : media;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded image", e);
        }
    }

}