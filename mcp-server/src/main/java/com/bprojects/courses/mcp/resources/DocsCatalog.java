package com.bprojects.courses.mcp.resources;

import com.bprojects.courses.mcp.McpServerApplication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The documents under this module's {@code src/main/resources/docs}, keyed by their
 * path relative to that folder — subfolders included.
 *
 * <p>The catalog is the security boundary for {@link DocsResources}: a read is served
 * only when the requested path is a key in here. Because the keys come from scanning
 * — never from the request — no amount of {@code ..}, absolute paths, or encoding
 * tricks in a client-supplied path can reach a file the scan did not already publish.
 *
 * <p>Scanning is anchored on this application's own code source, so the catalog stops
 * at this module: a dependency jar shipping its own {@code docs/} folder shares the
 * classpath but not that location, and is therefore never published.
 */
@Component
public class DocsCatalog {

    /** Folder under {@code src/main/resources} whose contents are published. */
    private static final String DOCS_FOLDER = "docs/";

    private final Map<String, Resource> documentsByPath;

    DocsCatalog() {
        this(new PathMatchingResourcePatternResolver(McpServerApplication.class.getClassLoader()));
    }

    DocsCatalog(ResourcePatternResolver resolver) {
        this.documentsByPath = scan(resolver);
    }

    /** Relative paths of every published document, sorted, e.g. {@code ["README.md", "guides/uris.md"]}. */
    public Set<String> paths() {
        return documentsByPath.keySet();
    }

    /** The document at {@code path}, or empty when it is not published — an unknown path is not an error here. */
    public Optional<Resource> find(String path) {
        return Optional.ofNullable(documentsByPath.get(path));
    }

    private static Map<String, Resource> scan(ResourcePatternResolver resolver) {
        String root = docsRootUrl();
        Map<String, Resource> found = new TreeMap<>();
        try {
            for (Resource resource : resolver.getResources("classpath*:" + DOCS_FOLDER + "**")) {
                if (!resource.isReadable()) {
                    continue;
                }
                String relative = relativePath(resource, root);
                if (relative != null) {
                    found.put(relative, resource);
                }
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not scan this module's docs folder", ex);
        }
        return Collections.unmodifiableMap(found);
    }

    /**
     * The docs folder inside this module's own classpath entry: {@code target/classes/docs/}
     * when run from Maven or the IDE, {@code ...jar!/BOOT-INF/classes/docs/} inside a Boot jar.
     */
    private static String docsRootUrl() {
        var codeSource = McpServerApplication.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("No code source for " + McpServerApplication.class
                    + "; cannot determine which classpath entry belongs to this module");
        }
        String url = codeSource.getLocation().toString();
        return (url.endsWith("/") ? url : url + "/") + DOCS_FOLDER;
    }

    /** {@code path} under {@code root}, or {@code null} when the resource lives outside this module's docs folder. */
    private static String relativePath(Resource resource, String root) {
        String url;
        try {
            url = resource.getURL().toString();
        }
        catch (IOException ex) {
            return null;
        }
        if (!url.startsWith(root) || url.endsWith("/")) {
            return null;
        }
        String relative = url.substring(root.length());
        return relative.isEmpty() ? null : relative;
    }
}
