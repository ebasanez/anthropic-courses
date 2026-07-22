package com.bprojects.courses.claude.config;

import com.bprojects.courses.claude.rag.SemanticTextSplitter;
// Vector store is the pgvector-autoconfigured PgVectorStore (Postgres-backed, persistent).
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RAG wiring. Active only under the {@code rag} profile; otherwise these beans
 * do not exist and the app runs as a pure-Claude chat app.
 *
 * <p>The {@link VectorStore} is autoconfigured by the pgvector starter
 * (a {@code PgVectorStore} backed by Postgres) — no manual bean here.
 *
 * <p>Both splitters are registered; the ingestion service picks one per request
 * (see {@code splitMethod}), defaulting to {@code rag.splitter}.
 */
@Configuration
@Profile("rag")
class RagConfig {

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore).build();
    }

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
