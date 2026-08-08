# Graph Report - .  (2026-08-08)

## Corpus Check
- Corpus is ~18,324 words - fits in a single context window. You may not need a graph.

## Summary
- 421 nodes · 910 edges · 19 communities (15 shown, 4 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 74 edges (avg confidence: 0.81)
- Token cost: 64,181 input · 16,839 output

## Community Hubs (Navigation)
- Config, Docs & Web UI
- RAG Client & Logging Config
- Chat API & Attachments
- Chunking & Ingestion
- App Entrypoints & MCP Docs
- MCP Resources & Tests
- Tone Evaluation Harness
- REST Controllers
- Tool Implementations
- Claude Prompt Orchestration
- Tool Registry & Toggles
- File Analysis via SDK
- Maven Wrapper Script
- Dependency Management Rules
- Chat Module Package
- Parent POM Package
- Embedding Server Package
- MCP Server Package

## God Nodes (most connected - your core abstractions)
1. `ChatRequestInputDto` - 20 edges
2. `ToolsService` - 20 edges
3. `ClaudeService` - 19 edges
4. `McpResourceService` - 19 edges
5. `DocumentIngestionService` - 16 edges
6. `FileAnalysisService` - 15 edges
7. `Tone` - 15 edges
8. `SemanticTextSplitter` - 14 edges
9. `EmbeddingServerVectorStore` - 13 edges
10. `TextEditorTool` - 13 edges

## Surprising Connections (you probably didn't know these)
- `APA 7 note on citing Wikipedia (title in the author slot)` --semantically_similar_to--> `Document URI encoding rule (percent-encode slashes in subfolder paths)`  [INFERRED] [semantically similar]
  util/El_Planeta_Tierra_APA.pdf → mcp-server/src/main/resources/docs/guides/uris.md
- `RAG document delete flow (delegated click, encodeURIComponent id)` --semantically_similar_to--> `Document URI encoding rule (percent-encode slashes in subfolder paths)`  [INFERRED] [semantically similar]
  chat/src/main/resources/static/index.html → mcp-server/src/main/resources/docs/guides/uris.md
- `El Planeta Tierra (APA 7 formatted Spanish PDF sample)` --conceptually_related_to--> `rag.splitter default TOKEN (TOKEN | SEMANTIC)`  [AMBIGUOUS]
  util/El_Planeta_Tierra_APA.pdf → embedding-server/src/main/resources/application.yaml
- `Keep unused autoconfiguration off the classpath, not excluded` --semantically_similar_to--> `Test config: exclude DataSource + PgVectorStore autoconfiguration`  [INFERRED] [semantically similar]
  CLAUDE.md → chat/src/test/resources/application.yaml
- `El Planeta Tierra (APA 7 formatted Spanish PDF sample)` --conceptually_related_to--> `RAG upload flow (multipart files + splitMethod, no page reload)`  [INFERRED]
  util/El_Planeta_Tierra_APA.pdf → chat/src/main/resources/static/index.html

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **RAG flow: browser upload -> chat proxy -> embedding-server chunking/embedding -> pgvector -> retrieval** — chat_src_main_resources_static_index_rag_upload_flow, chat_src_main_resources_application_embedding_server_base_url, claude_rag_split, embedding_server_src_main_resources_application_rag_splitter, embedding_server_src_main_resources_application_ollama_embedding_model, embedding_server_src_main_resources_application_pgvector_store, chat_src_main_resources_static_index_loaddocuments [INFERRED 0.85]
- **MCP contract: chat client profile, mcp-server endpoint, and the docs:// resource protocol** — chat_src_main_resources_application_mcp_client_config, chat_src_main_resources_application_mcp_connection_anthropic_courses_tools, mcp_server_src_main_resources_application_mcp_server_config, mcp_server_src_main_resources_application_mcp_server_instructions, mcp_server_src_main_resources_docs_readme_docs_listing_uri, mcp_server_src_main_resources_docs_guides_uris_document_uri_encoding, claude_mcp_resource_at_reference [EXTRACTED 1.00]
- **Container-backed state: pinned compose project, named volumes, datasource, idempotent schema, tests opting out** — embedding_server_compose_pinned_project_name, embedding_server_compose_ollama, embedding_server_compose_pgvector, embedding_server_src_main_resources_application_datasource, embedding_server_src_main_resources_application_docker_compose_enabled, chat_src_test_resources_application_no_containers, claude_module_working_directory_requirement [INFERRED 0.85]

## Communities (19 total, 4 thin omitted)

### Community 0 - "Config, Docs & Web UI"
Cohesion: 0.05
Nodes (52): chat Anthropic API key + model (ANTHROPIC_API_KEY, claude-sonnet-4-6), Prompt-cache strategy (SYSTEM_AND_TOOLS), embedding.server.base-url (http://localhost:8081), chat MCP client config (SYNC, 30s timeout, toolcallback enabled), streamable-http connection 'anthropic-courses-tools' -> localhost:8082/mcp, chat upload limits (100MB/file, 200MB/request, max-swallow-size -1), chat: no autoconfigure excludes needed (VectorStore SPI only), tools.text-editor.enabled: false (+44 more)

### Community 1 - "RAG Client & Logging Config"
Cohesion: 0.07
Nodes (29): Chain, Configuration, Override, LoggingInterceptor, ModelLoggingConfig, Builder, RagConfig, EmbeddingServerVectorStore (+21 more)

### Community 2 - "Chat API & Attachments"
Cohesion: 0.10
Nodes (14): ClaudeController, AttachmentKind, IMAGE, OFFICE, PDF, TEXT, Attachments, DocumentHelper (+6 more)

### Community 3 - "Chunking & Ingestion"
Cohesion: 0.09
Nodes (18): EmbeddingConfig, TokenTextSplitter, SearchRequestDto, SearchResultDto, Override, SemanticTextSplitter, DocumentIngestionService, SearchService (+10 more)

### Community 4 - "App Entrypoints & MCP Docs"
Cohesion: 0.10
Nodes (10): ChatApplication, EmbeddingServerApplication, McpServerApplication, DocsCatalog, DocsResources, DocsResourcesTest, org.springframework.ai.mcp.annotation.McpResource, org.springframework.boot.autoconfigure.SpringBootApplication (+2 more)

### Community 5 - "MCP Resources & Tests"
Cohesion: 0.13
Nodes (10): McpResourceService, EmbeddingServerVectorStoreTest, McpResourceServiceTest, ToolSchemaGenerationTest, io.modelcontextprotocol.client.McpSyncClient, io.modelcontextprotocol.spec.McpSchema.ResourceContents, java.util.regex.Pattern, org.junit.jupiter.api.Test (+2 more)

### Community 6 - "Tone Evaluation Harness"
Cohesion: 0.13
Nodes (11): ObjectMapper, ChatApplicationTests, ClaudeServiceToneEvaluationTest, EvalTask, Grader, org.junit.jupiter.api.BeforeAll, org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable, org.junit.jupiter.api.DisplayName (+3 more)

### Community 7 - "REST Controllers"
Cohesion: 0.15
Nodes (10): ConfigController, RagController, DocumentController, SearchController, DocumentInfo, org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.DeleteMapping, org.springframework.web.bind.annotation.GetMapping (+2 more)

### Community 8 - "Tool Implementations"
Cohesion: 0.14
Nodes (8): DateTimeTools, Override, Override, TextEditorTool, MathTools, MathToolsTest, org.springframework.ai.tool.annotation.Tool, org.springframework.stereotype.Component

### Community 9 - "Claude Prompt Orchestration"
Cohesion: 0.17
Nodes (13): ClaudeService, Builder, Tone, KIND, NEUTRAL, RUDE, ChatClientRequestSpec, org.springframework.ai.anthropic.AnthropicCacheStrategy (+5 more)

### Community 10 - "Tool Registry & Toggles"
Cohesion: 0.20
Nodes (6): ToolsService, MyTools, ToolDefinitionsVO, org.springframework.ai.tool.definition.ToolDefinition, org.springframework.ai.tool.ToolCallback, org.springframework.core.env.Environment

### Community 11 - "File Analysis via SDK"
Cohesion: 0.23
Nodes (6): FileAnalysisService, com.anthropic.client.AnthropicClient, com.anthropic.models.messages.CodeExecutionResultBlock, com.anthropic.models.messages.Message, com.anthropic.models.messages.MessageCreateParams, org.springframework.ai.anthropic.AnthropicChatModel

### Community 12 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 13 - "Dependency Management Rules"
Cohesion: 0.67
Nodes (3): Declare an artifact explicitly whenever its classes are imported, Nearest-definition mediation downgrade hazard (victools pinned to 5.x), Centralized Spring AI BOM version management

## Ambiguous Edges - Review These
- `rag.splitter default TOKEN (TOKEN | SEMANTIC)` → `El Planeta Tierra (APA 7 formatted Spanish PDF sample)`  [AMBIGUOUS]
  util/El_Planeta_Tierra_APA.pdf · relation: conceptually_related_to

## Knowledge Gaps
- **24 isolated node(s):** `chat`, `IMAGE`, `PDF`, `TEXT`, `OFFICE` (+19 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `rag.splitter default TOKEN (TOKEN | SEMANTIC)` and `El Planeta Tierra (APA 7 formatted Spanish PDF sample)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `MyTools` connect `Tool Registry & Toggles` to `Tool Implementations`, `Claude Prompt Orchestration`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Why does `ClaudeService` connect `Claude Prompt Orchestration` to `RAG Client & Logging Config`, `Chat API & Attachments`, `Chunking & Ingestion`, `MCP Resources & Tests`, `Tone Evaluation Harness`, `Tool Registry & Toggles`, `File Analysis via SDK`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Why does `ToolsService` connect `Tool Registry & Toggles` to `RAG Client & Logging Config`, `Chunking & Ingestion`, `Claude Prompt Orchestration`, `REST Controllers`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `chat`, `IMAGE`, `PDF` to the rest of the system?**
  _24 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Config, Docs & Web UI` be split into smaller, more focused modules?**
  _Cohesion score 0.053544494720965306 - nodes in this community are weakly interconnected._
- **Should `RAG Client & Logging Config` be split into smaller, more focused modules?**
  _Cohesion score 0.07372549019607844 - nodes in this community are weakly interconnected._