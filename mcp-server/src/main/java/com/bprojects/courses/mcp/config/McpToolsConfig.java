package com.bprojects.courses.mcp.config;

import com.bprojects.courses.mcp.tools.MathTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the MCP server: the {@link ToolCallbackProvider} the MCP
 * autoconfiguration publishes as tools.
 */
@Configuration
class McpToolsConfig {

    @Bean
    ToolCallbackProvider mathToolCallbacks(MathTools mathTools) {
        return MethodToolCallbackProvider.builder().toolObjects(mathTools).build();
    }
}
