# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 4.0.6 application (`com.bprojects.courses.claude`, group `com.bprojects.courses`, artifact `claude`), built with Java 25 and Maven. It depends on `spring-ai-starter-model-anthropic` (Spring AI BOM `2.0.0-RC2`) for Claude integration via Spring AI's Anthropic chat client. The codebase is currently a fresh skeleton: a single `@SpringBootApplication` entry point and a default context-load test, no controllers/services yet.

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd` on Windows) — no need for a separately installed Maven.

- Build: `./mvnw clean install`
- Run the app: `./mvnw spring-boot:run`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=ClaudeApplicationTests`
- Run a single test method: `./mvnw test -Dtest=ClaudeApplicationTests#contextLoads`

If `mvnw` fails due to a Java version mismatch (project requires Java 25), use the `java-switch` skill to switch the active JDK via SDKMAN and re-run.

## Architecture notes

- `src/main/java/com/bprojects/courses/claude/ClaudeApplication.java` — main Spring Boot entry point.
- `src/main/resources/application.properties` — currently only sets `spring.application.name=claude`. Spring AI's Anthropic starter requires an API key (typically `spring.ai.anthropic.api-key` / `ANTHROPIC_API_KEY` env var) and model configuration before chat calls will work — these are not yet configured.
- Dependency versions for Spring AI artifacts are managed centrally via the `spring-ai-bom` import in `dependencyManagement`; add new Spring AI starters without specifying a version.
