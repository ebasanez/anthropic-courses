package com.bprojects.courses.claude.config;

import com.bprojects.courses.claude.rag.EmbeddingServerVectorStore;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * RAG wiring. Active only under the {@code rag} profile; otherwise these beans
 * do not exist and the app runs as a pure-Claude chat app.
 *
 * <p>Retrieval is remote: the {@link VectorStore} is an {@link EmbeddingServerVectorStore}
 * pointing at the embedding-server module ({@code embedding.server.base-url}), which owns
 * the embedding model and the pgvector database. Chunking and ingestion live there too.
 */
@Configuration
@Profile("rag")
class RagConfig {

    /** Shared HTTP client for the embedding server (retrieval + document proxying). */
    @Bean
    RestClient embeddingServerRestClient(
            RestClient.Builder builder,
            @Value("${embedding.server.base-url:http://localhost:8081}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    VectorStore vectorStore(RestClient embeddingServerRestClient) {
        return new EmbeddingServerVectorStore(embeddingServerRestClient);
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore).build();
    }
}
