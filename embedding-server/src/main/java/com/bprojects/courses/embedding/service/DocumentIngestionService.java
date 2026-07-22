package com.bprojects.courses.embedding.service;

import com.bprojects.courses.embedding.rag.SemanticTextSplitter;
import com.bprojects.courses.embedding.vo.DocumentInfo;
import com.bprojects.courses.embedding.vo.RagSplitMethod;
// Vector store is the pgvector-autoconfigured PgVectorStore (Postgres-backed, persistent).
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Ingests uploaded documents into the vector store. Tika auto-detects the
 * format, so plain text, PDF, and Markdown all go through one path.
 */
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter tokenSplitter;
    private final SemanticTextSplitter semanticSplitter;
    private final RagSplitMethod defaultSplitMethod;
    private final TokenCountEstimator tokenCounter = new JTokkitTokenCountEstimator();

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
        RagSplitMethod effective = method != null ? method : defaultSplitMethod;
        TextSplitter splitter = splitterFor(effective);
        int chunks = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            long size = file.getSize();
            String docId = UUID.randomUUID().toString();   // per-file id, used for deletion
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
            // Tag each document with docId + filename + size so it can be listed and
            // deleted later. Metadata propagates to every chunk produced by the splitter.
            List<Document> docs = new TikaDocumentReader(resource).read().stream()
                    .map(d -> d.mutate()
                            .metadata("docId", docId)
                            .metadata("filename", name)
                            .metadata("size", size)
                            .build())
                    .toList();
            List<Document> split = splitter.split(docs);

            // Per-document stats: chunking algorithm + average tokens per chunk.
            long avgTokens = split.isEmpty() ? 0 : Math.round(
                    split.stream().mapToInt(d -> tokenCounter.estimate(d.getText())).average().orElse(0));
            List<Document> enriched = split.stream()
                    .map(d -> d.mutate()
                            .metadata("splitMethod", effective.name())
                            .metadata("avgTokensPerChunk", avgTokens)
                            .build())
                    .toList();

            vectorStore.add(enriched);
            chunks += enriched.size();
        }
        return chunks;
    }

    private TextSplitter splitterFor(RagSplitMethod method) {
        return switch (method) {
            case TOKEN -> tokenSplitter;
            case SEMANTIC -> semanticSplitter;
        };
    }

    /** Distinct documents currently in the store, keyed by docId, with per-file chunk count. */
    public List<DocumentInfo> listDocuments() {
        String sql = """
                SELECT metadata->>'docId' AS id,
                       MAX(metadata->>'filename') AS name,
                       MAX((metadata->>'size')::bigint) AS size,
                       COUNT(*) AS chunks,
                       MAX(metadata->>'splitMethod') AS split_method,
                       MAX((metadata->>'avgTokensPerChunk')::bigint) AS avg_tokens
                FROM vector_store
                WHERE metadata->>'docId' IS NOT NULL
                GROUP BY metadata->>'docId'
                ORDER BY name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new DocumentInfo(rs.getString("id"), rs.getString("name"), rs.getLong("size"),
                        rs.getLong("chunks"), rs.getString("split_method"), rs.getLong("avg_tokens")));
    }

    /**
     * Remove every chunk belonging to the given document id (UUID). Returns the
     * number of chunks deleted (0 if no document with that id exists).
     */
    public int deleteDocument(String id) {
        return jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'docId' = ?", id);
    }
}
