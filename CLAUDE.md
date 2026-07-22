# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Multi-module Maven project built with Java 25:

- root `pom.xml` — parent aggregator, `com.bprojects.courses:claude-parent`, packaging `pom`. Inherits `spring-boot-starter-parent` 4.0.6, imports the Spring AI BOM (`2.0.0-RC2`), and declares **all** dependencies so modules inherit them. Add new dependencies here, not in the modules.
- `chat/` — Spring Boot app (`com.bprojects.courses:chat`, package `com.bprojects.courses.claude`), port 8080. Claude chat, tools, file analysis, the web UI, and the RAG *client* side.
- `embedding-server/` — Spring Boot app (`com.bprojects.courses:embedding-server`, package `com.bprojects.courses.embedding`), port 8081. Owns the Ollama embedding model, the pgvector store, chunking/ingestion, and `compose.yaml`.

Claude integration goes through Spring AI's Anthropic chat client (`spring-ai-starter-model-anthropic`). RAG is split across the two apps: `chat` retrieves over HTTP, `embedding-server` embeds and stores.

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd` on Windows) — no need for a separately installed Maven.

Run from the repository root (the reactor builds every module):

- Build: `./mvnw clean install`
- Run the chat app: `./mvnw -pl chat spring-boot:run` (add `-Dspring-boot.run.profiles=rag` for RAG)
- Run the embedding server: `./mvnw -pl embedding-server spring-boot:run` (starts the Ollama + pgvector containers)
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=ChatApplicationTests`
- Run a single test method: `./mvnw test -Dtest=ChatApplicationTests#contextLoads`

If `mvnw` fails due to a Java version mismatch (project requires Java 25), use the `java-switch` skill to switch the active JDK via SDKMAN and re-run.

## Architecture notes

- `chat/src/main/java/com/bprojects/courses/claude/ChatApplication.java` — chat entry point; `embedding-server/src/main/java/com/bprojects/courses/embedding/EmbeddingServerApplication.java` — embedding entry point.
- `chat/src/main/resources/application.yaml` — API key (`ANTHROPIC_API_KEY` env var), model, cache strategy, upload limits, `embedding.server.base-url`. DataSource + PgVectorStore autoconfig are excluded in *every* profile: this module never owns a vector store.
- `embedding-server/src/main/resources/application.yaml` — port, datasource, Ollama embedding model, pgvector settings, chunking defaults (`rag.splitter`, `rag.semantic.*`).
- **RAG split**: under the `rag` profile `chat` builds an `EmbeddingServerVectorStore` (`chat/.../rag/`) — a read-only `VectorStore` whose `similaritySearch` POSTs to `/api/embeddings/search`; the `QuestionAnswerAdvisor` retrieves through it. Filters travel as Spring AI filter DSL text (printed with `PrintFilterExpressionConverter`, re-parsed server-side); `EmbeddingServerVectorStoreTest` pins that round trip. `RagController` proxies the UI's `/api/ai/documents` calls to `/api/embeddings/documents` so the browser stays single-origin. Writes through the client-side store throw `UnsupportedOperationException` by design.
- `embedding-server/compose.yaml` — Ollama + pgvector containers, project name pinned to `anthropic-courses` so volumes survive file moves. **Each app must run with its own module directory as working directory**: `spring-boot-docker-compose` resolves `compose.yaml` against the process working directory, as does `./workspace` (chat's text-editor tool base dir). `./mvnw -pl <module> spring-boot:run` already does this (the plugin defaults to `${project.basedir}`); IntelliJ run configurations need *Working directory* = `$MODULE_WORKING_DIR$`, otherwise Boot fails with `No Docker Compose file found in directory '<repo root>'`.
- Dependency versions for Spring AI artifacts are managed centrally via the `spring-ai-bom` import in the parent `dependencyManagement`; add new Spring AI starters without specifying a version. Both modules inherit every dependency, so unused autoconfig is turned off with `spring.ai.model.*` / `spring.autoconfigure.exclude` rather than by trimming the classpath.
- The `spring-boot-maven-plugin` is version-managed in the parent's `pluginManagement` and enabled (repackage) in each application module.
