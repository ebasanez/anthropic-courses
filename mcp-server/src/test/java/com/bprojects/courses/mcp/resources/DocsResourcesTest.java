package com.bprojects.courses.mcp.resources;

import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocsResourcesTest {

    private final DocsResources resources = new DocsResources(new DocsCatalog());

    @Test
    void listsTheDocsFolderWithPathsRelativeToIt() {
        assertThat(resources.listing().lines()).containsExactly(
                "docs://mcp-server/README.md",
                "docs://mcp-server/guides%2Furis.md");
    }

    @Test
    void readsADocumentAtTheFolderRoot() {
        var contents = (TextResourceContents) resources.document("README.md").getFirst();

        assertThat(contents.uri()).isEqualTo("docs://mcp-server/README.md");
        assertThat(contents.mimeType()).isEqualTo("text/markdown");
        assertThat(contents.text()).contains("published over");
    }

    @Test
    void readsANestedDocumentThroughItsEncodedPath() {
        var contents = (TextResourceContents) resources.document("guides%2Furis.md").getFirst();

        assertThat(contents.uri()).isEqualTo("docs://mcp-server/guides%2Furis.md");
        assertThat(contents.text()).contains("percent-encoded");
    }

    @Test
    void refusesPathsOutsideTheDocsFolder() {
        // The catalog is an allowlist built by scanning, so traversal has nothing to hit:
        // encoded or not, an unlisted path is simply absent.
        assertThatThrownBy(() -> resources.document("..%2Fapplication.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such document");

        assertThatThrownBy(() -> resources.document("%2Fetc%2Fpasswd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotPublishAnythingOutsideDocs() {
        // application.yaml sits one level up, in src/main/resources itself.
        assertThat(resources.listing()).doesNotContain("application.yaml");
    }
}
