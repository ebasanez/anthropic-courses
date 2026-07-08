package com.bprojects.courses.claude.vo;

/** One document loaded into the RAG store: original filename, byte size, and how many chunks it produced. */
public record DocumentInfo(String name, long size, long chunks) {
}
