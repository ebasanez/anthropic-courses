package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.rag.SemanticTextSplitter;
import com.bprojects.courses.claude.vo.DocumentInfo;
import com.bprojects.courses.claude.vo.RagSplitMethod;
// Vector store is the pgvector-autoconfigured PgVectorStore (Postgres-backed, persistent).
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Ingests uploaded documents into the vector store. Tika auto-detects the
 * format, so plain text, PDF, and Markdown all go through one path.
 * Active only under the {@code rag} profile.
 */
@Service
@Profile("rag")
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter tokenSplitter;
    private final SemanticTextSplitter semanticSplitter;
    private final RagSplitMethod defaultSplitMethod;

    public DocumentIngestionService(VectorStore vectorStore,
                                    JdbcTemplate jdbcTemplate,
                                    TokenTextSplitter tokenSplitter,
                                    SemanticTextSplitter semanticSplitter,
                                    @Value("${rag.splitter:TOKEN}") RagSplitMethod defaultSplitMethod) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.tokenSplitter = tokenSplitter;
        this.semanticSplitter = semanticSplitter;
        this.defaultSplitMethod = defaultSplitMethod;
    }

    /** Ingest using the configured default split method ({@code rag.splitter}). */
    public int ingest(MultipartFile[] files) throws IOException {
        return ingest(files, null);
    }

    /**
     * Ingest with an explicit split method. When {@code method} is {@code null},
     * falls back to the {@code rag.splitter} default.
     */
    public int ingest(MultipartFile[] files, RagSplitMethod method) throws IOException {
        TextSplitter splitter = splitterFor(method != null ? method : defaultSplitMethod);
        int chunks = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            long size = file.getSize();
            Resource resource = new InputStreamResource(file.getInputStream()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();   // extension = Tika format hint
                }

                @Override
                public long contentLength() {
                    return file.getSize();
                }
            };
            // Tag each document with filename + size so it can be listed later.
            // Metadata propagates to every chunk produced by the splitter.
            List<Document> docs = new TikaDocumentReader(resource).read().stream()
                    .map(d -> d.mutate()
                            .metadata("filename", name)
                            .metadata("size", size)
                            .build())
                    .toList();
            List<Document> split = splitter.split(docs);
            vectorStore.add(split);
            chunks += split.size();
        }
        return chunks;
    }

    private TextSplitter splitterFor(RagSplitMethod method) {
        return switch (method) {
            case TOKEN -> tokenSplitter;
            case SEMANTIC -> semanticSplitter;
        };
    }

    /** Distinct documents currently in the store, with total chunk count per file. */
    public List<DocumentInfo> listDocuments() {
        String sql = """
                SELECT metadata->>'filename' AS name,
                       MAX((metadata->>'size')::bigint) AS size,
                       COUNT(*) AS chunks
                FROM vector_store
                WHERE metadata->>'filename' IS NOT NULL
                GROUP BY metadata->>'filename'
                ORDER BY name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new DocumentInfo(rs.getString("name"), rs.getLong("size"), rs.getLong("chunks")));
    }
}
