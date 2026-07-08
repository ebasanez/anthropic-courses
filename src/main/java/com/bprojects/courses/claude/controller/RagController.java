package com.bprojects.courses.claude.controller;

import com.bprojects.courses.claude.service.DocumentIngestionService;
import com.bprojects.courses.claude.vo.DocumentInfo;
import com.bprojects.courses.claude.vo.RagSplitMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Document upload endpoint for RAG. Exists only under the {@code rag} profile;
 * otherwise {@code POST /api/ai/documents} returns 404.
 */
@RestController
@RequestMapping("/api/ai")
@Profile("rag")
public class RagController {

    private final DocumentIngestionService ingestion;

    public RagController(DocumentIngestionService ingestion) {
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

    // List the documents currently loaded in the RAG store: name + size (+ chunk count).
    @GetMapping("/documents")
    public List<DocumentInfo> list() {
        return ingestion.listDocuments();
    }
}
