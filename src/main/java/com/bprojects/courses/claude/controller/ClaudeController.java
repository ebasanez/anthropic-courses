package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.service.ClaudeService;
import com.bprojects.courses.claude.vo.Tone;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String chat(@RequestParam(value = "message") String message,
                        @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                        @RequestParam(value = "conversationId", required = false) String conversationId,
                       @RequestParam(value = "tone", required = false) Tone tone) {
        return claudeService.generateResponse(message, maxTokens, conversationId, tone);
    }

    // Server-Sent Events (SSE) for real-time text streaming
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(value = "message") String message) {
        return claudeService.streamResponse(message);
    }

}