package com.bprojects.courses.claude.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The store sends the filter to the embedding server as DSL text, which the server
 * re-parses. These tests pin that contract: what we print must parse back identically.
 */
class EmbeddingServerVectorStoreTest {

    private static final String HITS = """
            [{"id":"c1","text":"chunk one","metadata":{"docId":"d1","filename":"a.txt"},"score":0.82}]
            """;

    @Test
    void searchPostsQueryAndMapsHits() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8081/api/embeddings/search"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.query").value("what is earth"))
                .andExpect(jsonPath("$.filterExpression").doesNotExist())
                .andRespond(withSuccess(HITS, MediaType.APPLICATION_JSON));

        var store = new EmbeddingServerVectorStore(builder.build());
        List<Document> hits = store.similaritySearch(SearchRequest.builder().query("what is earth").build());

        server.verify();
        assertThat(hits).singleElement().satisfies(d -> {
            assertThat(d.getId()).isEqualTo("c1");
            assertThat(d.getText()).isEqualTo("chunk one");
            assertThat(d.getScore()).isEqualTo(0.82);
            assertThat(d.getMetadata()).containsEntry("docId", "d1");
        });
    }

    @Test
    void docIdFilterSurvivesTheRoundTripToTheServer() {
        // Same filter shape ClaudeService builds when the user scopes RAG to selected documents.
        String dsl = "docId in ['1e1e2b3c-1111-2222-3333-444455556666', '2f2f3c4d-7777-8888-9999-aaaabbbbcccc']";
        SearchRequest request = SearchRequest.builder().query("q").filterExpression(dsl).build();

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8081/api/embeddings/search"))
                .andExpect(jsonPath("$.filterExpression").exists())
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        new EmbeddingServerVectorStore(builder.build()).similaritySearch(request);
        server.verify();

        // What the store printed must parse back into the very same expression the advisor had.
        String printed = new org.springframework.ai.vectorstore.filter.converter.PrintFilterExpressionConverter()
                .convertExpression(request.getFilterExpression());
        assertThat(new FilterExpressionTextParser().parse(printed))
                .isEqualTo(request.getFilterExpression());
    }

    @Test
    void writesAreRejected() {
        var store = new EmbeddingServerVectorStore(RestClient.builder().baseUrl("http://localhost:8081").build());
        assertThatThrownBy(() -> store.add(List.of(new Document("x"))))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> store.delete(List.of("id")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
