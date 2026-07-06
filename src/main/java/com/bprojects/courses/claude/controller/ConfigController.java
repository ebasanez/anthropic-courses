package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.service.ToolsService;
import com.bprojects.courses.claude.vo.ToolDefinitionsVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ToolsService toolsService;
    private final ObjectMapper objectMapper;

    public ConfigController(ToolsService toolsService, ObjectMapper objectMapper) {
        this.toolsService = toolsService;
        this.objectMapper = objectMapper;
    }

    // Schemas of all registered tools
    @GetMapping("/tools")
    public Map<String, Object> tools() {
        ToolDefinitionsVO tools = toolsService.getToolDefinitions();
        return Map.of("nativeTools", tools.nativeTools(),
                "customTools",
                (Object)tools.customTools().stream()
                        .map(t -> Map.<String, Object>of(
                                "name", t.name(),
                                "description", t.description(),
                                "inputSchema", parseJson(t.inputSchema())))
                        .toList());
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid tool input schema JSON", e);
        }
    }
}
