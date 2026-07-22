package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.dto.ChatRequestInputDto;
import com.bprojects.courses.claude.dto.ChatRequestWithMediaInputDto;
import com.bprojects.courses.claude.service.ClaudeService;
import com.bprojects.courses.claude.service.FileAnalysisService;
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
    private final FileAnalysisService fileAnalysisService;

    public ClaudeController(ClaudeService claudeService, FileAnalysisService fileAnalysisService) {
        this.claudeService = claudeService;
        this.fileAnalysisService = fileAnalysisService;
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
        if (request.isFileAnalysis()) {
            return claudeService.generateResponse(analysisPrompt(request),
                    request.getMaxTokens(), request.getTemperature(), request.getThinkingBudget(),
                    request.getRagDocumentIds(), request.getConversationId(), request.getTone());
        }
        DocumentHelper.Attachments att = DocumentHelper.toAttachments(request.getMedia());
        return claudeService.generateResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithMedia(ChatRequestWithMediaInputDto request) {
        if (request.isFileAnalysis()) {
            return claudeService.streamResponse(analysisPrompt(request),
                    request.getMaxTokens(), request.getTemperature(), request.getThinkingBudget(),
                    request.getRagDocumentIds(), request.getConversationId(), request.getTone());
        }
        DocumentHelper.Attachments att = DocumentHelper.toAttachments(request.getMedia());
        return claudeService.streamResponse(request.getMessage(), request.getMaxTokens(), request.getTemperature(),
                request.getThinkingBudget(), request.getRagDocumentIds(), request.getConversationId(), request.getTone(),
                att.media(), att.citationDocuments());
    }

    /**
     * Run the attached data file through code execution and fold the findings into the user's
     * message. The file itself is not re-attached: its content is already represented by the
     * analysis text, and sending it again invites Claude to hunt for it on disk.
     */
    private String analysisPrompt(ChatRequestWithMediaInputDto request) {
        var target = DocumentHelper.firstAnalysable(request.getMedia());
        String analysis = fileAnalysisService.analyze(request.getMessage(), target);
        return request.getMessage() + "\n\n" + analysis;
    }

}
