package com.bprojects.courses.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP server: exposes the project's document knowledge base (owned by the
 * embedding-server module) as MCP tools, so any MCP client — Claude Code,
 * Claude Desktop, another agent — can search it without going through the
 * chat application.
 */
@SpringBootApplication
public class McpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

}
