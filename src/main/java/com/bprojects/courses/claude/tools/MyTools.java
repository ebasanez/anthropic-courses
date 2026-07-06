package com.bprojects.courses.claude.tools;

/**
 * Marker interface for tool beans. Every bean implementing this is a candidate tool;
 * a tool is enabled unless {@code tools.{toolName}.enabled=false} is set.
 */
public interface MyTools {

    /**
     * Logical tool name, used to build the {@code tools.{toolName}.enabled} property key.
     */
    String getToolName();
}
