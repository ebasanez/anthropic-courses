package com.bprojects.courses.embedding.controller;

import com.bprojects.courses.embedding.dto.SearchRequestDto;
import com.bprojects.courses.embedding.dto.SearchResultDto;
import com.bprojects.courses.embedding.service.SearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Retrieval endpoint used by the chat application's RAG advisor. */
@RestController
@RequestMapping("/api/embeddings")
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<SearchResultDto> search(@RequestBody SearchRequestDto request) {
        return search.search(request);
    }
}
