package com.bprojects.courses.claude.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File-oriented text editor tool. All operations are confined to a configured
 * workspace directory ({@code tools.text-editor.base-dir}); paths escaping it are rejected.
 */
@Component
public class TextEditorTool implements MyTools {

    private final Path baseDir;

    public TextEditorTool(@Value("${tools.text-editor.base-dir:./workspace}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public String getToolName() {
        return "text-editor";
    }

    @Tool(description = "View a text file's content (with 1-based line numbers), or list a directory's entries.")
    String view(@ToolParam(description = "Path relative to the workspace root, e.g. 'notes/todo.txt'.") String path) {
        Path target = resolve(path);
        if (Files.isDirectory(target)) {
            try (var entries = Files.list(target)) {
                return entries.map(p -> baseDir.relativize(p).toString())
                        .sorted()
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("(empty directory)");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        List<String> lines = readLines(target);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i + 1).append('\t').append(lines.get(i)).append('\n');
        }
        return sb.isEmpty() ? "(empty file)" : sb.toString();
    }

    @Tool(description = "Create or overwrite a text file with the given content.")
    String create(
            @ToolParam(description = "Path relative to the workspace root.") String path,
            @ToolParam(description = "Full text content to write.") String content) {
        Path target = resolve(path);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "Wrote " + baseDir.relativize(target);
    }

    @Tool(description = "Replace a unique text snippet in a file. Fails if the snippet is missing or appears more than once.")
    String strReplace(
            @ToolParam(description = "Path relative to the workspace root.") String path,
            @ToolParam(description = "Exact text to find. Must occur exactly once in the file.") String oldText,
            @ToolParam(description = "Replacement text.") String newText) {
        Path target = resolve(path);
        String content = readString(target);
        int first = content.indexOf(oldText);
        if (first < 0) {
            return "No match found for the given text.";
        }
        if (content.indexOf(oldText, first + 1) >= 0) {
            return "Text is not unique; it appears multiple times. Provide a longer, unique snippet.";
        }
        writeString(target, content.substring(0, first) + newText + content.substring(first + oldText.length()));
        return "Replaced 1 occurrence in " + baseDir.relativize(target);
    }

    @Tool(description = "Insert text after a given 1-based line number (use 0 to insert at the beginning).")
    String insert(
            @ToolParam(description = "Path relative to the workspace root.") String path,
            @ToolParam(description = "1-based line number to insert after; 0 inserts at the start.") int afterLine,
            @ToolParam(description = "Text to insert (a single line, no trailing newline needed).") String text) {
        Path target = resolve(path);
        List<String> lines = new ArrayList<>(readLines(target));
        if (afterLine < 0 || afterLine > lines.size()) {
            return "Line number out of range: file has " + lines.size() + " lines.";
        }
        lines.add(afterLine, text);
        writeString(target, String.join("\n", lines) + "\n");
        return "Inserted 1 line into " + baseDir.relativize(target);
    }

    // Resolve a user path under the workspace, rejecting traversal outside it.
    private Path resolve(String path) {
        Path resolved = baseDir.resolve(path).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path escapes the workspace: " + path);
        }
        return resolved;
    }

    private List<String> readLines(Path target) {
        try {
            return Files.readAllLines(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readString(Path target) {
        try {
            return Files.readString(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeString(Path target, String content) {
        try {
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
