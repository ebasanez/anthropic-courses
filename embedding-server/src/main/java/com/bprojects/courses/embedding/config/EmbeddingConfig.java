package com.bprojects.courses.embedding.config;

import com.bprojects.courses.embedding.rag.SemanticTextSplitter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ingestion wiring.
 *
 * <p>The {@link VectorStore} is autoconfigured by the pgvector starter
 * (a {@code PgVectorStore} backed by Postgres) — no manual bean here.
 *
 * <p>Both splitters are registered; the ingestion service picks one per request
 * (see {@code splitMethod}), defaulting to {@code rag.splitter}.
 */
@Configuration
class EmbeddingConfig {

    /** Fixed-size token windows — fast. */
    @Bean
    TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /** Embedding-based topic breakpoints — slower; threshold via {@code rag.semantic.breakpoint-percentile}. */
    @Bean
    SemanticTextSplitter semanticTextSplitter(
            EmbeddingModel embeddingModel,
            @Value("${rag.semantic.breakpoint-percentile:95.0}") double breakpointPercentile,
            @Value("${rag.semantic.max-chunk-chars:4000}") int maxChunkChars) {
        return new SemanticTextSplitter(embeddingModel, breakpointPercentile, maxChunkChars);
    }
}
