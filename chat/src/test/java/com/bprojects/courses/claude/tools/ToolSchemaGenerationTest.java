package com.bprojects.courses.claude.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the JSON-schema generation behind {@code @Tool}, which is a dependency-resolution
 * tripwire as much as a functional one.
 *
 * <p>{@code JsonSchemaGenerator} loads victools' {@code JacksonSchemaModule} in a static
 * initialiser, and Spring AI needs victools 5.x. The Anthropic SDK ships 4.38.0; whenever
 * a shorter path to that older version wins Maven's mediation, every tool call fails at
 * runtime with {@code NoClassDefFoundError} — while the rest of the suite still passes,
 * because nothing else touches this initialiser. Assert it here so a dependency change
 * cannot break tool calling silently.
 */
class ToolSchemaGenerationTest {

    @Test
    void buildsToolDefinitionsForAnnotatedMethods() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new DateTimeTools())
                .build()
                .getToolCallbacks();

        assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
                .contains("getCurrentDateTime", "addDuration");
        assertThat(callbacks).allSatisfy(callback ->
                assertThat(callback.getToolDefinition().inputSchema()).contains("json-schema.org"));
    }
}
