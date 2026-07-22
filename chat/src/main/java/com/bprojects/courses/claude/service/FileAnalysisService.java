package com.bprojects.courses.claude.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.MultipartField;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileUploadParams;
import com.anthropic.models.messages.CodeExecutionResultBlock;
import com.anthropic.models.messages.CodeExecutionTool20260120;
import com.anthropic.models.messages.ContainerUploadBlockParam;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs Claude's server-side code execution against an uploaded file.
 * <p>
 * Anthropic only lets code execution reach a file that was uploaded through the Files API and
 * referenced with a {@code container_upload} block — inline document content is not visible to
 * the sandbox. Spring AI's {@code ChatClient} cannot express either of those, so this service
 * talks to the Anthropic SDK directly (reusing the autoconfigured client). The returned text is
 * then fed back through {@link ClaudeService}, which keeps memory, RAG and tone intact.
 */
@Service
public class FileAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FileAnalysisService.class);

    // Code execution is still beta; the Files API beta is needed for the upload to be visible.
    private static final String BETA_HEADER = "code-execution-2025-08-25,files-api-2025-04-14";

    // The analysis burns tokens on tool traffic before it ever writes prose, so it needs far
    // more headroom than a normal chat turn (a 4096 budget dies with stop_reason=max_tokens).
    private static final long MAX_TOKENS = 16384;

    private static final String INSTRUCTIONS = """
            Analyse the attached file to answer the question below. The file is available in the \
            code execution environment. Load it, inspect it, and compute whatever is needed. \
            Finish with a concise summary of your findings in plain prose.

            Question: """;

    private final AnthropicClient client;
    private final String model;

    public FileAnalysisService(AnthropicChatModel chatModel,
                               @Value("${spring.ai.anthropic.chat.model:claude-sonnet-4-6}") String model) {
        this.client = chatModel.getAnthropicClient();
        this.model = model;
    }

    /**
     * Upload the file, let Claude analyse it with code execution, and return the findings as
     * text: the narrative plus the output of every successful program run. The upload is always
     * deleted afterwards so the workspace does not accumulate files.
     */
    public String analyze(String question, MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String fileId = upload(file, filename);
        try {
            Message response = client.messages().create(buildRequest(question, fileId));
            logUsage(response, filename);
            return format(filename, response);
        } finally {
            deleteQuietly(fileId);
        }
    }

    private String upload(MultipartFile file, String filename) {
        try {
            // Must be a ByteArrayInputStream: the SDK's multipart serializer only reads the
            // content of that specific type. A disk-backed stream (what MultipartFile hands
            // back once Tomcat spools the upload) silently uploads as 0 bytes.
            InputStream content = new ByteArrayInputStream(file.getBytes());
            // The filename must survive the upload too: Claude refers to the file by name in
            // the code it writes, and the sandbox materialises it under that name.
            MultipartField<InputStream> field = MultipartField.<InputStream>builder()
                    .value(content)
                    .filename(filename)
                    .contentType(file.getContentType() != null
                            ? file.getContentType() : "application/octet-stream")
                    .build();
            FileMetadata uploaded = client.beta().files().upload(FileUploadParams.builder()
                    .file(field)
                    .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                    .build());
            if (uploaded.sizeBytes() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Upload of '" + filename + "' arrived empty at Anthropic.");
            }
            log.info("uploaded '{}' as {} ({} bytes)", filename, uploaded.id(), uploaded.sizeBytes());
            return uploaded.id();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read '" + filename + "' for upload", e);
        }
    }

    private MessageCreateParams buildRequest(String question, String fileId) {
        List<ContentBlockParam> blocks = List.of(
                ContentBlockParam.ofContainerUpload(
                        ContainerUploadBlockParam.builder().fileId(fileId).build()),
                ContentBlockParam.ofText(
                        TextBlockParam.builder().text(INSTRUCTIONS + question).build()));

        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .addTool(CodeExecutionTool20260120.builder().build())
                .addUserMessageOfBlockParams(blocks)
                .putAdditionalHeader("anthropic-beta", BETA_HEADER)
                .build();
    }

    /**
     * Collect the useful parts of the response: prose from text blocks, and stdout from runs
     * that exited cleanly. Failed runs are skipped — Claude retries after an error, so only the
     * successful attempts describe the real state of the data.
     */
    private String format(String filename, Message response) {
        List<String> narrative = new ArrayList<>();
        List<String> output = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            block.text().ifPresent(text -> {
                if (!text.text().isBlank()) {
                    narrative.add(text.text().strip());
                }
            });
            block.codeExecutionToolResult().ifPresent(result ->
                    result.content().resultBlock().ifPresent(r -> collectStdout(r, output)));
            block.bashCodeExecutionToolResult().ifPresent(result ->
                    result.content().bashCodeExecutionResultBlock()
                            .ifPresent(r -> {
                                if (r.returnCode() == 0 && !r.stdout().isBlank()) {
                                    output.add(r.stdout().strip());
                                }
                            }));
        }

        if (narrative.isEmpty() && output.isEmpty()) {
            String reason = response.stopReason().map(Object::toString).orElse("unknown");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "File analysis produced no result for '" + filename + "' (stop reason: " + reason + ").");
        }

        StringBuilder sb = new StringBuilder("Analysis of ").append(filename).append(":\n");
        if (!narrative.isEmpty()) {
            sb.append("\n[Claude's narrative]\n").append(String.join("\n\n", narrative)).append('\n');
        }
        if (!output.isEmpty()) {
            sb.append("\n[Program output]\n").append(String.join("\n", output)).append('\n');
        }
        return sb.toString();
    }

    private static void collectStdout(CodeExecutionResultBlock result, List<String> output) {
        if (result.returnCode() == 0 && !result.stdout().isBlank()) {
            output.add(result.stdout().strip());
        }
    }

    private void logUsage(Message response, String filename) {
        var usage = response.usage();
        log.info("file analysis '{}': input={} output={} cacheRead={} stopReason={}",
                filename, usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L),
                response.stopReason().map(Object::toString).orElse("-"));
    }

    private void deleteQuietly(String fileId) {
        try {
            client.beta().files().delete(fileId);
        } catch (RuntimeException e) {
            // Not fatal: the answer is already computed, the upload just lingers in the workspace.
            log.warn("Could not delete uploaded file {}: {}", fileId, e.getMessage());
        }
    }
}
