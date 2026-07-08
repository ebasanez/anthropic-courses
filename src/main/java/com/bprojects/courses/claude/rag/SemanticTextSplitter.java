package com.bprojects.courses.claude.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Semantic chunking: splits text into sentences, embeds each one, and starts a
 * new chunk wherever the embedding distance between consecutive sentences spikes
 * (a topic shift). The breakpoint threshold is the given percentile of all
 * consecutive-sentence distances, so it adapts to each document.
 *
 * <p>Note: embeds every sentence at ingest — slower than {@code TokenTextSplitter}.
 * Sentence splitting is a simple regex; swap for {@code java.text.BreakIterator}
 * or a real sentence detector for higher quality.
 */
public class SemanticTextSplitter extends TextSplitter {

    // Embed sentences in batches so a large document doesn't become one huge (timeout-prone) request.
    private static final int EMBED_BATCH_SIZE = 64;

    private final EmbeddingModel embeddingModel;
    private final double breakpointPercentile;   // e.g. 95.0 — higher = fewer, larger chunks

    public SemanticTextSplitter(EmbeddingModel embeddingModel, double breakpointPercentile) {
        this.embeddingModel = embeddingModel;
        this.breakpointPercentile = breakpointPercentile;
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            return sentences;
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
            if (dist[i] > threshold) {                                 // topic shift -> cut here
                chunks.add(cur.toString().trim());
                cur = new StringBuilder();
            }
            cur.append(' ').append(sentences.get(i + 1));
        }
        if (!cur.isEmpty()) {
            chunks.add(cur.toString().trim());
        }
        return chunks;
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
