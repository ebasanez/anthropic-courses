package com.bprojects.courses.embedding.dto;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/** One retrieved chunk: its id, text, metadata and similarity score. */
public record SearchResultDto(
        String id,
        String text,
        Map<String, Object> metadata,
        @Nullable Double score) {
}
