# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Multi-module Maven project built with Java 25:

- root `pom.xml` — parent aggregator, `com.bprojects.courses:claude-parent`, packaging `pom`. Inherits `spring-boot-starter-parent` 4.0.6 and imports the Spring AI BOM (`2.0.0-RC2`). It carries **`dependencyManagement` only** — no inherited `<dependencies>`. Each module declares the artifacts it actually uses, without versions. Pin a version in the parent only when neither BOM manages the artifact (currently `anthropic-java-core`, `okhttp`, `okio`, `mcp-core`).
- `chat/` — Spring Boot app (`com.bprojects.courses:chat`, package `com.bprojects.courses.claude`), port 8080. Claude chat, tools, file analysis, the web UI, and the RAG *client* side.
- `embedding-server/` — Spring Boot app (`com.bprojects.courses:embedding-server`, package `com.bprojects.courses.embedding`), port 8081. Owns the Ollama embedding model, the pgvector store, chunking/ingestion, and `compose.yaml`.
- `mcp-server/` — Spring Boot app (`com.bprojects.courses:mcp-server`, package `com.bprojects.courses.mcp`), port 8082. Publishes tools over MCP (streamable HTTP at `/mcp`); owns no model or database.

Claude integration goes through Spring AI's Anthropic chat client (`spring-ai-starter-model-anthropic`). RAG is split across the two apps: `chat` retrieves over HTTP, `embedding-server` embeds and stores.

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd` on Windows) — no need for a separately installed Maven.

Run from the repository root (the reactor builds every module):

- Build: `./mvnw clean install`
- Run the chat app: `./mvnw -pl chat spring-boot:run` (add `-Dspring-boot.run.profiles=rag` for RAG)
- Run the embedding server: `./mvnw -pl embedding-server spring-boot:run` (starts the Ollama + pgvector containers)
- Run the MCP server: `./mvnw -pl mcp-server spring-boot:run`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=ChatApplicationTests`
- Run a single test method: `./mvnw test -Dtest=ChatApplicationTests#contextLoads`

If `mvnw` fails due to a Java version mismatch (project requires Java 25), use the `java-switch` skill to switch the active JDK via SDKMAN and re-run.

## Architecture notes

- `chat/src/main/java/com/bprojects/courses/claude/ChatApplication.java` — chat entry point; `embedding-server/src/main/java/com/bprojects/courses/embedding/EmbeddingServerApplication.java` — embedding entry point.
- `chat/src/main/resources/application.yaml` — API key (`ANTHROPIC_API_KEY` env var), model, cache strategy, upload limits, `embedding.server.base-url`. No autoconfigure excludes are needed: the module declares the `spring-ai-vector-store` SPI but no store implementation and no JDBC driver, so there is nothing to switch off.
- `embedding-server/src/main/resources/application.yaml` — port, datasource, Ollama embedding model, pgvector settings, chunking defaults (`rag.splitter`, `rag.semantic.*`).
- **RAG split**: under the `rag` profile `chat` builds an `EmbeddingServerVectorStore` (`chat/.../rag/`) — a read-only `VectorStore` whose `similaritySearch` POSTs to `/api/embeddings/search`; the `QuestionAnswerAdvisor` retrieves through it. Filters travel as Spring AI filter DSL text (printed with `PrintFilterExpressionConverter`, re-parsed server-side); `EmbeddingServerVectorStoreTest` pins that round trip. `RagController` proxies the UI's `/api/ai/documents` calls to `/api/embeddings/documents` so the browser stays single-origin. Writes through the client-side store throw `UnsupportedOperationException` by design.
- **MCP module**: `MathTools` exposes `add` as a `@Tool` method; `McpToolsConfig` publishes it through a `MethodToolCallbackProvider`, which the MCP autoconfiguration turns into an MCP tool. Add further tools by annotating methods and passing the object to `toolObjects(...)`. Register the server with a client via `claude mcp add --transport http anthropic-courses http://localhost:8082/mcp`.
- Only `mcp-server` declares the MCP *server* starter, so only it serves `/mcp`. `chat` and `embedding-server` no longer need `spring.ai.mcp.server.enabled: false`.
- **MCP client** (`chat`, `mcp` profile): `chat` declares `spring-ai-starter-mcp-client`, but connections live in `chat/src/main/resources/application-mcp.yaml`, so the starter is inert without the profile and `chat` never hard-depends on `mcp-server`. Run with `-Dspring-boot.run.profiles=mcp` (combine as `rag,mcp`). `ToolsService` collects the remote `ToolCallback`s from the autoconfigured `ToolCallbackProvider` via `ObjectProvider` — empty when the profile is off — and `ChatService` attaches them per request with `spec.toolCallbacks(...)`, alongside the annotated `MyTools` beans that go through `spec.tools(...)`. Remote tools also show up in `GET /api/config/tools`. Note the enable/disable switches in `ToolsService` cover local `MyTools` beans only; MCP tools are all-or-nothing with the profile.
- **MCP resources** are client-driven, so they need their own path: `McpResourceService` (`chat/.../service/`) resolves `@<uri>` references typed into the chat input — `@docs://mcp-server/README.md` — reading them through `McpSyncClient` and prepending the content in a `<referenced-resources>` block. It hooks into the single `ChatService.buildPromptSpec`, so both the blocking and streaming paths get it. Unresolvable references become inline error entries rather than exceptions. `GET /api/config/resources` lists what the connected servers advertise; note `resources/list` returns the fixed resources and the *templates* (`docs://mcp-server/{path}`), so concrete document URIs come from reading the listing resource itself.
- `embedding-server/compose.yaml` — Ollama + pgvector containers, project name pinned to `anthropic-courses` so volumes survive file moves. **Each app must run with its own module directory as working directory**: `spring-boot-docker-compose` resolves `compose.yaml` against the process working directory, as does `./workspace` (chat's text-editor tool base dir). `./mvnw -pl <module> spring-boot:run` already does this (the plugin defaults to `${project.basedir}`); IntelliJ run configurations need *Working directory* = `$MODULE_WORKING_DIR$`, otherwise Boot fails with `No Docker Compose file found in directory '<repo root>'`.
- Dependency versions for Spring AI artifacts are managed centrally via the `spring-ai-bom` import in the parent `dependencyManagement`; add new Spring AI starters to a module without specifying a version. Unused autoconfiguration is kept off **by not putting it on the classpath** — a module declares only the starters it needs — rather than by `spring.ai.model.*: none` guards or `spring.autoconfigure.exclude`. Declare an artifact explicitly whenever the module imports its classes directly, even if a starter already pulls it in transitively (`spring-ai-mcp-annotations` and `mcp-core` in `mcp-server`, the Anthropic SDK and okhttp in `chat`) — IntelliJ flags such imports as unresolved, and the transitive path can vanish on an upgrade.
- Trimming a module's dependencies can silently drop autoconfiguration that arrived transitively. `chat` needs an explicit `spring-boot-restclient`: `spring-boot-starter-web` does not provide `RestClient.Builder`, which `RagConfig` requires. Verify a trim by starting each app, not just by running tests — `contextLoads` does not cover the `rag` profile.
- Declaring an artifact directly also moves its transitives one hop closer, which can win Maven's nearest-definition mediation and **downgrade** a shared library. That is why `com.github.victools:*` is pinned to 5.x in the parent: `anthropic-java-core` ships 4.38.0, and once `chat` declared the SDK directly, tool calling died at runtime with `NoClassDefFoundError: …JacksonSchemaModule` while every test still passed. `ToolSchemaGenerationTest` now covers that path. After changing dependencies, check `./mvnw -pl <module> dependency:tree -Dverbose | grep "omitted for conflict"` — conflicts that resolve to an *older* version are the dangerous ones.
- The `spring-boot-maven-plugin` is version-managed in the parent's `pluginManagement` and enabled (repackage) in each application module.
