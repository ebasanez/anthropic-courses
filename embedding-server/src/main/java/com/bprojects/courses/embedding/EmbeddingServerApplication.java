package com.bprojects.courses.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone embedding server: owns the Ollama embedding model and the pgvector
 * store. Exposes document ingestion and similarity search over HTTP so the chat
 * application never talks to the vector database directly.
 */
@SpringBootApplication
public class EmbeddingServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmbeddingServerApplication.class, args);
	}

}
