# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Multi-module Maven project built with Java 25:

- root `pom.xml` — parent aggregator, `com.bprojects.courses:claude-parent`, packaging `pom`. Inherits `spring-boot-starter-parent` 4.0.6, imports the Spring AI BOM (`2.0.0-RC2`), and declares **all** dependencies so modules inherit them. Add new dependencies here, not in the modules.
- `chat/` — the Spring Boot application module (`com.bprojects.courses:chat`, package `com.bprojects.courses.claude`). Holds the sources, resources and `compose.yaml`.

Claude integration goes through Spring AI's Anthropic chat client (`spring-ai-starter-model-anthropic`); RAG uses Ollama embeddings + pgvector.

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd` on Windows) — no need for a separately installed Maven.

Run from the repository root (the reactor builds every module):

- Build: `./mvnw clean install`
- Run the app: `./mvnw -pl chat spring-boot:run`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=ChatApplicationTests`
- Run a single test method: `./mvnw test -Dtest=ChatApplicationTests#contextLoads`

If `mvnw` fails due to a Java version mismatch (project requires Java 25), use the `java-switch` skill to switch the active JDK via SDKMAN and re-run.

## Architecture notes

- `chat/src/main/java/com/bprojects/courses/claude/ChatApplication.java` — main Spring Boot entry point.
- `chat/src/main/resources/application.yaml` — API key (`ANTHROPIC_API_KEY` env var), model, cache strategy, RAG and upload settings.
- `chat/compose.yaml` — Ollama + pgvector containers. **The app must run with `chat/` as its working directory**: `spring-boot-docker-compose` resolves `compose.yaml` against the process working directory, as does `./workspace` (text-editor tool base dir). `./mvnw -pl chat spring-boot:run` already does this (the plugin defaults to `${project.basedir}`); IntelliJ run configurations need *Working directory* = `$MODULE_WORKING_DIR$`, otherwise Boot fails with `No Docker Compose file found in directory '<repo root>'`.
- Dependency versions for Spring AI artifacts are managed centrally via the `spring-ai-bom` import in the parent `dependencyManagement`; add new Spring AI starters without specifying a version.
- The `spring-boot-maven-plugin` is version-managed in the parent's `pluginManagement` and enabled (repackage) only in `chat/pom.xml`.
