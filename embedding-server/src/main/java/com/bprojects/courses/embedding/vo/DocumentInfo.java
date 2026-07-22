package com.bprojects.courses.embedding.vo;

/**
 * One document loaded into the RAG store: generated id, original filename, byte size,
 * how many chunks it produced, the chunking algorithm used, and the average tokens per chunk.
 */
public record DocumentInfo(String id, String name, long size, long chunks,
                           String splitMethod, long avgTokensPerChunk) {
}
