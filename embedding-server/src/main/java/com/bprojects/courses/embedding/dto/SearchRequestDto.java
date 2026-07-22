package com.bprojects.courses.embedding.dto;

import org.jspecify.annotations.Nullable;

/**
 * Similarity-search request. {@code filterExpression} is Spring AI filter DSL
 * (e.g. {@code docId in ['uuid-1','uuid-2']}); {@code null} searches everything.
 */
public record SearchRequestDto(
        String query,
        @Nullable Integer topK,
        @Nullable Double similarityThreshold,
        @Nullable String filterExpression) {
}
