package com.bprojects.courses.embedding.service;

import com.bprojects.courses.embedding.dto.SearchRequestDto;
import com.bprojects.courses.embedding.dto.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Similarity search over the pgvector store, exposed to remote callers. */
@Service
public class SearchService {

    private final VectorStore vectorStore;

    public SearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SearchResultDto> search(SearchRequestDto request) {
        SearchRequest.Builder builder = SearchRequest.builder().query(request.query());
        Optional.ofNullable(request.topK()).ifPresent(builder::topK);
        Optional.ofNullable(request.similarityThreshold()).ifPresent(builder::similarityThreshold);
        // Filter DSL is parsed here (by the store's own parser), so callers only ever send text.
        if (request.filterExpression() != null && !request.filterExpression().isBlank()) {
            builder.filterExpression(request.filterExpression());
        }
        List<Document> hits = vectorStore.similaritySearch(builder.build());
        return hits == null ? List.of() : hits.stream()
                .map(d -> new SearchResultDto(d.getId(), d.getText(), d.getMetadata(), d.getScore()))
                .toList();
    }
}
