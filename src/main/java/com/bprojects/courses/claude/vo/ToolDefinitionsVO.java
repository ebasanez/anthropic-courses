package com.bprojects.courses.claude.vo;

import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

public record ToolDefinitionsVO(List<String> nativeTools, List<ToolDefinition> customTools) {
}
