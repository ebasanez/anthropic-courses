package com.bprojects.courses.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools published over MCP. Method names become tool names and the descriptions
 * are what a client model reads, so they carry the usage rules.
 */
@Component
public class MathTools {

    @Tool(description = "Add two numbers and return their sum.")
    public double add(
            @ToolParam(description = "First addend.") double a,
            @ToolParam(description = "Second addend.") double b) {
        return a + b;
    }
}
