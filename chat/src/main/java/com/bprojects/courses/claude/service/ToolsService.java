package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.tools.MyTools;
import com.bprojects.courses.claude.vo.ToolDefinitionsVO;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolsService {

    // Each tool mapped to whether it is currently enabled.
    private final Map<MyTools, Boolean> toolStates = new ConcurrentHashMap<>();
    private final Environment environment;

    public ToolsService(List<MyTools> tools, Environment environment) {
        this.environment = environment;
        for (MyTools tool : tools) {
            toolStates.put(tool, isEnabledByProperty(tool, environment));
        }
    }

    // Claude's native web search tool is on unless tools.web-search.enabled=false
    public boolean isNativeWebSearchToolEnabled() {
        return !"false".equals(environment.getProperty("tools.web-search.enabled"));
    }

    // Tools not disabled via tools.{toolName}.enabled=false
    private boolean isEnabledByProperty(MyTools tool, Environment environment) {
        String key = "tools." + tool.getToolName() + ".enabled";
        return environment.getProperty(key, Boolean.class, true);
    }

    public List<MyTools> getEnabledTools() {
        return toolStates.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    // Enable exactly the given tools, disable the rest
    public void updateEnabledTools(List<MyTools> enabledTools) {
        toolStates.replaceAll((tool, state) -> enabledTools.contains(tool));
    }

    public void enableTool(String name) {
        setEnabled(name, true);
    }

    public void disableTool(String name) {
        setEnabled(name, false);
    }

    private void setEnabled(String name, boolean enabled) {
        toolStates.replaceAll((tool, state) ->
                tool.getToolName().equals(name) ? enabled : state);
    }

    // Schemas of all enabled tools
    public ToolDefinitionsVO getToolDefinitions() {
        List<String> nativeTools = new ArrayList<>();
        if (isNativeWebSearchToolEnabled()){
            nativeTools.add("web-search");
        }
        List<ToolDefinition> customTools =  Arrays.stream(ToolCallbacks.from(getEnabledTools().toArray()))
                .map(ToolCallback::getToolDefinition)
                .toList();
        return new ToolDefinitionsVO(nativeTools, customTools);
    }
}
