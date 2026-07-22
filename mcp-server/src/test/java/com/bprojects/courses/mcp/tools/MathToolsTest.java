package com.bprojects.courses.mcp.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MathToolsTest {

    private final MathTools tools = new MathTools();

    @Test
    void addsTwoNumbers() {
        assertThat(tools.add(2, 3)).isEqualTo(5.0);
        assertThat(tools.add(-1.5, 0.5)).isEqualTo(-1.0);
    }
}
