package com.bprojects.courses.claude.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Document endpoints for the chat UI. Exists only under the {@code rag} profile;
 * otherwise {@code /api/ai/documents} returns 404.
 *
 * <p>Ingestion and storage belong to the embedding-server module, so these are a
 * thin proxy onto its {@code /api/embeddings/documents} API: the browser keeps
 * talking to a single origin, and no vector-store code lives in this module.
 */
@RestController
@RequestMapping("/api/ai")
@Profile("rag")
public class RagController {

    private final RestClient embeddingServer;

    public RagController(RestClient embeddingServerRestClient) {
        this.embeddingServer = embeddingServerRestClient;
    }

    // POST multipart: one or more .txt / .pdf / .md files.
    // Optional splitMethod (TOKEN | SEMANTIC); when absent, the embedding server's default applies.
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "splitMethod", required = false) @Nullable String splitMethod)
            throws IOException {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            form.add("files", asResource(file));
        }
        if (splitMethod != null && !splitMethod.isBlank()) {
            form.add("splitMethod", splitMethod);
        }
        return embeddingServer.post()
                .uri("/api/embeddings/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })   // pass the server's status/body through
                .toEntity(String.class);
    }

    // Documents currently loaded in the RAG store: id, name, size, chunk count, split stats.
    @GetMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> list() {
        return embeddingServer.get()
                .uri("/api/embeddings/documents")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })   // pass the server's status/body through
                .toEntity(String.class);
    }

    // Remove a document (all its chunks) by its generated id (UUID); 404 when unknown.
    @DeleteMapping(value = "/documents/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> delete(@PathVariable("id") String id) {
        return embeddingServer.delete()
                .uri("/api/embeddings/documents/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })   // pass the server's status/body through
                .toEntity(String.class);
    }

    // Re-wrap the upload so the forwarded part keeps its filename (Tika's format hint).
    private static Resource asResource(MultipartFile file) throws IOException {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }
}
