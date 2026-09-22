package com.bprojects.courses.mcp.resources;

import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The documents in this module's {@code src/main/resources/docs}, published over MCP.
 *
 * <p>Two resources: a fixed one listing what is available, and a template that reads
 * one document. A client normally reads the listing first, since it hands back the
 * exact URIs the template accepts.
 *
 * <p><b>Encoding.</b> An MCP URI template variable matches {@code ([^/]+)} — it stops
 * at a slash — so a document in a subfolder like {@code guides/uris.md} cannot be
 * dropped into {@code {path}} as-is. Slashes travel percent-encoded
 * ({@code guides%2Furis.md}) and are decoded here. The listing publishes the encoded
 * form, so callers do not have to know this rule.
 */
@Component
public class DocsResources {

    /** Scheme and prefix shared by the listing and the per-document template. */
    private static final String URI_PREFIX = "docs://mcp-server/";

    private final DocsCatalog catalog;

    public DocsResources(DocsCatalog catalog) {
        this.catalog = catalog;
    }

    @McpResource(
            uri = "docs/",
            name = "module-docs",
            title = "mcp-server documents",
            description = """
                    Lists every document under this module's src/main/resources/docs, one URI per \
                    line. Read one of those URIs to get the document's contents.""",
            mimeType = "text/plain")
    public String listing() {
        return String.join("\n", catalog.paths().stream().map(path -> URI_PREFIX + encode(path)).toList());
    }

    /**
     * Reads one document from the docs folder.
     *
     * @param path the document's path relative to {@code src/main/resources/docs}, with any
     *             slashes percent-encoded — {@code README.md}, {@code guides%2Furis.md}
     */
    @McpResource(
            uri = "docs/{path}",
            name = "module-document",
            title = "mcp-server document",
            description = """
                    One document from this module's src/main/resources/docs. The path is relative \
                    to that folder with slashes percent-encoded (guides%2Furis.md). Read \
                    docs://mcp-server for the list of valid URIs.""")
    public List<ResourceContents> document(String path) {
        String relative = decode(path);
        Resource resource = catalog.find(relative).orElseThrow(() -> new IllegalArgumentException(
                "No such document: '" + relative + "'. Read docs://mcp-server for the available paths."));

        String uri = URI_PREFIX + encode(relative);
        String mimeType = mimeTypeOf(relative);
        byte[] bytes = read(resource);

        return List.of(isText(mimeType)
                ? new TextResourceContents(uri, mimeType, new String(bytes, StandardCharsets.UTF_8))
                : new BlobResourceContents(uri, mimeType, Base64.getEncoder().encodeToString(bytes)));
    }

    private static byte[] read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return in.readAllBytes();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + resource, ex);
        }
    }

    /**
     * Only the extensions documents are likely to use are mapped; anything unrecognised is
     * treated as binary and base64-encoded, which is the safe direction to be wrong in.
     */
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("md", "text/markdown"),
            Map.entry("txt", "text/plain"),
            Map.entry("adoc", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("yaml", "application/yaml"),
            Map.entry("yml", "application/yaml"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("pdf", "application/pdf"));

    private static String mimeTypeOf(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static boolean isText(String mimeType) {
        return mimeType.startsWith("text/")
                || mimeType.equals("application/json")
                || mimeType.equals("application/yaml")
                || mimeType.equals("application/xml")
                || mimeType.equals("image/svg+xml");
    }

    /** Percent-encodes the separators only; {@code URLEncoder} would turn spaces into {@code +}. */
    private static String encode(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String path) {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }
}
