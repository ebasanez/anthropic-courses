package com.bprojects.courses.claude.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpResourceServiceTest {

    private static final String README = "docs://mcp-server/README.md";
    private static final String NESTED = "docs://mcp-server/guides%2Furis.md";

    @Test
    void inlinesTheContentOfAReferencedResource() {
        McpSyncClient client = clientServing(README, "# mcp-server documents");
        var service = new McpResourceService(providerOf(List.of(client)));

        String expanded = service.expandReferences("Summarise @" + README + " please");

        assertThat(expanded).contains("<resource uri=\"" + README + "\">")
                .contains("# mcp-server documents")
                .contains("</referenced-resources>")
                // the reference stays put, so the model sees where it was used
                .contains("Summarise @" + README + " please");
    }

    @Test
    void resolvesPercentEncodedSubfolderPaths() {
        McpSyncClient client = clientServing(NESTED, "# Document URIs");
        var service = new McpResourceService(providerOf(List.of(client)));

        assertThat(service.expandReferences("@" + NESTED)).contains("# Document URIs");
    }

    @Test
    void stripsSentencePunctuationFromTheReference() {
        McpSyncClient client = clientServing(README, "body");
        var service = new McpResourceService(providerOf(List.of(client)));

        assertThat(service.expandReferences("see @" + README + ", thanks")).contains("body");
    }

    @Test
    void leavesPlainUrlsAlone() {
        McpSyncClient client = mock(McpSyncClient.class);
        var service = new McpResourceService(providerOf(List.of(client)));

        String message = "compare with https://example.com/README.md";

        assertThat(service.expandReferences(message)).isEqualTo(message);
        verify(client, never()).readResource(any(io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest.class));
    }

    @Test
    void reportsUnreadableResourcesInlineRatherThanFailing() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.readResource(any(io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest.class)))
                .thenThrow(new IllegalStateException("no such document"));
        var service = new McpResourceService(providerOf(List.of(client)));

        String expanded = service.expandReferences("@docs://mcp-server/missing.md");

        assertThat(expanded).contains("could not be read").contains("no such document");
    }

    @Test
    void isANoOpWithoutAnMcpServer() {
        var service = new McpResourceService(providerOf(null));

        String message = "Summarise @" + README;

        assertThat(service.expandReferences(message)).isEqualTo(message);
        assertThat(service.listResourceUris()).isEmpty();
    }

    private static McpSyncClient clientServing(String uri, String text) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.readResource(any(io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest.class)))
                .thenReturn(new ReadResourceResult(
                        List.of(new TextResourceContents(uri, "text/markdown", text))));
        return client;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<List<McpSyncClient>> providerOf(List<McpSyncClient> clients) {
        ObjectProvider<List<McpSyncClient>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clients);
        return provider;
    }
}
