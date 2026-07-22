package com.bprojects.courses.claude.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Semantic chunking: splits text into sentences, embeds each one, and starts a
 * new chunk wherever the embedding distance between consecutive sentences spikes
 * (a topic shift). The breakpoint threshold is the given percentile of all
 * consecutive-sentence distances, so it adapts to each document.
 *
 * <p>A hard size cap ({@code maxChunkChars}) is enforced on top of the semantic
 * breakpoints: chunks are force-cut when they grow too large, and any chunk that
 * still exceeds the cap (e.g. a document with no sentence breaks, or one very
 * long sentence) is hard-split on token boundaries. This guarantees no single
 * chunk exceeds the embedding model's maximum input tokens — otherwise the vector
 * store's batching strategy throws
 * {@code "Tokens in a single document exceeds the maximum number of allowed input tokens"}.
 *
 * <p>Note: embeds every sentence at ingest — slower than {@code TokenTextSplitter}.
 * Sentence splitting is a simple regex; swap for {@code java.text.BreakIterator}
 * or a real sentence detector for higher quality.
 */
public class SemanticTextSplitter extends TextSplitter {

    // Embed sentences in batches so a large document doesn't become one huge (timeout-prone) request.
    private static final int EMBED_BATCH_SIZE = 64;

    // Default max characters per chunk. Kept well below the embedding token limit
    // even for token-dense text (worst case ~1 token/char stays under 8191).
    private static final int DEFAULT_MAX_CHUNK_CHARS = 4000;

    private final EmbeddingModel embeddingModel;
    private final double breakpointPercentile;   // e.g. 95.0 — higher = fewer, larger chunks
    private final int maxChunkChars;              // hard size cap per emitted chunk

    // Fallback splitter for chunks that still exceed the cap (token-bounded, ~800 tokens/piece).
    private final TextSplitter hardSplitter = TokenTextSplitter.builder().build();

    public SemanticTextSplitter(EmbeddingModel embeddingModel, double breakpointPercentile) {
        this(embeddingModel, breakpointPercentile, DEFAULT_MAX_CHUNK_CHARS);
    }

    public SemanticTextSplitter(EmbeddingModel embeddingModel, double breakpointPercentile, int maxChunkChars) {
        this.embeddingModel = embeddingModel;
        this.breakpointPercentile = breakpointPercentile;
        this.maxChunkChars = maxChunkChars > 0 ? maxChunkChars : DEFAULT_MAX_CHUNK_CHARS;
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            // No semantic breakpoints possible — still enforce the size cap so a
            // large, unpunctuated document doesn't get embedded as one huge chunk.
            List<String> out = new ArrayList<>();
            for (String s : sentences) {
                addCapped(out, s);
            }
            return out;
        }

        List<float[]> emb = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i += EMBED_BATCH_SIZE) {   // batched to avoid read timeouts
            emb.addAll(embeddingModel.embed(
                    sentences.subList(i, Math.min(i + EMBED_BATCH_SIZE, sentences.size()))));
        }

        double[] dist = new double[sentences.size() - 1];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = 1.0 - cosine(emb.get(i), emb.get(i + 1));         // distance = 1 - cosine similarity
        }

        double threshold = percentile(dist, breakpointPercentile);

        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder(sentences.get(0));
        for (int i = 0; i < dist.length; i++) {
            String next = sentences.get(i + 1);
            boolean topicShift = dist[i] > threshold;
            // Force a cut when the chunk would outgrow the size cap, even without a topic shift.
            boolean tooBig = cur.length() + 1 + next.length() > maxChunkChars;
            if (topicShift || tooBig) {                                // topic shift or size cap -> cut here
                addCapped(chunks, cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(next);
        }
        if (!cur.isEmpty()) {
            addCapped(chunks, cur.toString());
        }
        return chunks;
    }

    /**
     * Adds a chunk, guaranteeing it stays under the size cap. Chunks that still
     * exceed the cap (e.g. a single very long sentence) are hard-split on token
     * boundaries so no chunk can exceed the embedding model's input limit.
     */
    private void addCapped(List<String> chunks, String chunk) {
        if (chunk == null) {
            return;
        }
        String trimmed = chunk.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() <= maxChunkChars) {
            chunks.add(trimmed);
            return;
        }
        for (Document piece : hardSplitter.split(List.of(new Document(trimmed)))) {
            String t = piece.getText();
            if (t != null && !t.isBlank()) {
                chunks.add(t.trim());
            }
        }
    }

    private static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        for (String p : text.split("(?<=[.!?])\\s+")) {
            String s = p.strip();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
    }

    private static double percentile(double[] v, double p) {
        double[] s = v.clone();
        Arrays.sort(s);
        int idx = (int) Math.ceil(p / 100.0 * s.length) - 1;
        return s[Math.max(0, Math.min(idx, s.length - 1))];
    }
}
