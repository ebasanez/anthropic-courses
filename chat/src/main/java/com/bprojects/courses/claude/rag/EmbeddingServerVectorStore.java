package com.bprojects.courses.claude.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.PrintFilterExpressionConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Read-only {@link VectorStore} backed by the embedding-server module: similarity
 * search is delegated to {@code POST /api/embeddings/search} instead of hitting a
 * local vector database. This is what the {@code QuestionAnswerAdvisor} retrieves
 * through, so the chat application needs no embedding model and no datasource.
 *
 * <p>Writes are the embedding server's job — {@code add}/{@code delete} are not
 * supported here; use its {@code /api/embeddings/documents} endpoints.
 */
public class EmbeddingServerVectorStore implements VectorStore {

    // Filter.Expression -> Spring AI filter DSL text, which the server re-parses.
    private static final PrintFilterExpressionConverter FILTER_TO_TEXT = new PrintFilterExpressionConverter();

    private static final ParameterizedTypeReference<List<Hit>> HIT_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public EmbeddingServerVectorStore(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String getName() {
        return "embedding-server";
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        var body = new Query(
                request.getQuery(),
                request.getTopK(),
                request.getSimilarityThreshold(),
                request.hasFilterExpression() ? FILTER_TO_TEXT.convertExpression(request.getFilterExpression()) : null);

        List<Hit> hits = restClient.post()
                .uri("/api/embeddings/search")
                .body(body)
                .retrieve()
                .body(HIT_LIST);

        return hits == null ? List.of() : hits.stream()
                .map(h -> Document.builder()
                        .id(h.id())
                        .text(h.text())
                        .metadata(h.metadata() == null ? Map.of() : h.metadata())
                        .score(h.score())
                        .build())
                .toList();
    }

    @Override
    public void add(List<Document> documents) {
        throw new UnsupportedOperationException(
                "Read-only store: ingest through the embedding server's POST /api/embeddings/documents");
    }

    @Override
    public void delete(List<String> idList) {
        throw new UnsupportedOperationException(
                "Read-only store: delete through the embedding server's DELETE /api/embeddings/documents/{id}");
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException(
                "Read-only store: delete through the embedding server's DELETE /api/embeddings/documents/{id}");
    }

    private record Query(String query, Integer topK, Double similarityThreshold, String filterExpression) {
    }

    private record Hit(String id, String text, Map<String, Object> metadata, Double score) {
    }
}
