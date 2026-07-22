package com.bprojects.courses.embedding.controller;

import com.bprojects.courses.embedding.service.DocumentIngestionService;
import com.bprojects.courses.embedding.vo.DocumentInfo;
import com.bprojects.courses.embedding.vo.RagSplitMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Document lifecycle in the vector store: ingest, list, delete. */
@RestController
@RequestMapping("/api/embeddings")
public class DocumentController {

    private final DocumentIngestionService ingestion;

    public DocumentController(DocumentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    // POST multipart: one or more .txt / .pdf / .md files.
    // Optional splitMethod (TOKEN | SEMANTIC); when absent, uses the rag.splitter default.
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "splitMethod", required = false) RagSplitMethod splitMethod)
            throws IOException {
        int chunks = ingestion.ingest(files, splitMethod);
        return Map.of("filesReceived", files.length, "chunksIngested", chunks);
    }

    // List the documents currently loaded in the store: name + size (+ chunk count).
    @GetMapping("/documents")
    public List<DocumentInfo> list() {
        return ingestion.listDocuments();
    }

    // Remove a document (all its chunks) by its generated id (UUID).
    // 404 when no document with that id exists.
    @DeleteMapping("/documents/{id}")
    public Map<String, Object> delete(@PathVariable("id") String id) {
        int deleted = ingestion.deleteDocument(id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No document with id '" + id + "'");
        }
        return Map.of("id", id, "chunksDeleted", deleted);
    }
}
