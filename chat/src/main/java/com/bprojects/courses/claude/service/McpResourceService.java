package com.bprojects.courses.claude.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code @}-references to MCP resources that a user typed into the chat input.
 *
 * <p>MCP draws a hard line between tools and resources: the model decides when to call a
 * tool, but a resource is only ever fetched because the client application chose to. This
 * service is that choice — it gives the chat UI a reference syntax, the way an MCP host
 * like Claude Code gives one through @-mentions.
 *
 * <p>Syntax: {@code @} immediately followed by a resource URI, e.g.
 * {@code @docs://mcp-server/README.md}. The URI is exactly what {@code resources/list}
 * advertises (see {@code GET /api/config/resources}), so a subfolder document keeps its
 * percent-encoded slash: {@code @docs://mcp-server/guides%2Furis.md}.
 *
 * <p>Referenced content is prepended to the message in a tagged block; the reference itself
 * is left in place so the model can see where it was used. A resource that cannot be read
 * becomes an error entry rather than an exception — a bad reference should not sink the
 * whole conversation.
 */
@Service
public class McpResourceService {

    private static final Logger log = LoggerFactory.getLogger(McpResourceService.class);

    /** {@code @scheme://rest}, stopping at whitespace. Requires the {@code @} so plain URLs are left alone. */
    private static final Pattern REFERENCE = Pattern.compile("@([A-Za-z][A-Za-z0-9+.\\-]*://\\S+)");

    /** Sentence punctuation that almost certainly ends the reference rather than belonging to it. */
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}\"'";

    private final ObjectProvider<List<McpSyncClient>> mcpClients;

    public McpResourceService(ObjectProvider<List<McpSyncClient>> mcpClients) {
        this.mcpClients = mcpClients;
    }

    /**
     * Every resource URI the connected MCP servers advertise, plus their URI templates.
     * Empty when no MCP server is configured (the {@code mcp} profile is off).
     */
    public List<String> listResourceUris() {
        Set<String> uris = new LinkedHashSet<>();
        for (McpSyncClient client : clients()) {
            try {
                client.listResources().resources().forEach(resource -> uris.add(resource.uri()));
                client.listResourceTemplates().resourceTemplates()
                        .forEach(template -> uris.add(template.uriTemplate()));
            }
            catch (RuntimeException ex) {
                log.warn("Could not list resources from MCP server: {}", ex.getMessage());
            }
        }
        return List.copyOf(uris);
    }

    /**
     * Replaces nothing in {@code message}, but prepends the content of every resource it
     * references. Returns the message unchanged when it holds no references, when no MCP
     * server is connected, or when nothing could be resolved.
     */
    public String expandReferences(String message) {
        if (message == null || message.indexOf('@') < 0 || clients().isEmpty()) {
            return message;
        }
        Set<String> uris = referencedUris(message);
        if (uris.isEmpty()) {
            return message;
        }

        StringBuilder block = new StringBuilder("<referenced-resources>\n");
        for (String uri : uris) {
            block.append("<resource uri=\"").append(uri).append("\">\n");
            try {
                block.append(read(uri)).append('\n');
            }
            catch (RuntimeException ex) {
                log.warn("Could not read MCP resource {}: {}", uri, ex.getMessage());
                block.append("(could not be read: ").append(ex.getMessage()).append(")\n");
            }
            block.append("</resource>\n");
        }
        block.append("</referenced-resources>\n\n");
        return block + message;
    }

    /** Reads one resource and flattens its contents to text. Blobs are described, not inlined. */
    public String read(String uri) {
        for (McpSyncClient client : clients()) {
            var result = client.readResource(new ReadResourceRequest(uri));
            if (result != null && !result.contents().isEmpty()) {
                return flatten(result.contents());
            }
        }
        throw new IllegalArgumentException("no MCP server serves " + uri);
    }

    private static String flatten(List<ResourceContents> contents) {
        List<String> parts = new ArrayList<>();
        for (ResourceContents content : contents) {
            switch (content) {
                case TextResourceContents text -> parts.add(text.text());
                case BlobResourceContents blob ->
                        parts.add("(binary " + blob.mimeType() + ", omitted)");
                default -> parts.add("(unsupported content type, omitted)");
            }
        }
        return String.join("\n", parts);
    }

    /** URIs referenced in the message, in the order they appear, without duplicates. */
    private static Set<String> referencedUris(String message) {
        Set<String> uris = new LinkedHashSet<>();
        Matcher matcher = REFERENCE.matcher(message);
        while (matcher.find()) {
            String uri = trimTrailingPunctuation(matcher.group(1));
            if (!uri.isEmpty()) {
                uris.add(uri);
            }
        }
        return uris;
    }

    private static String trimTrailingPunctuation(String uri) {
        int end = uri.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(uri.charAt(end - 1)) >= 0) {
            end--;
        }
        return uri.substring(0, end);
    }

    private List<McpSyncClient> clients() {
        List<McpSyncClient> clients = mcpClients.getIfAvailable();
        return clients == null ? List.of() : clients;
    }
}
