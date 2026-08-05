# Document URIs

A document in a subfolder is addressed with its slashes percent-encoded, because an
MCP URI template variable matches `([^/]+)` and stops at a `/`.

This file lives at `docs/guides/uris.md` on disk, so its URI is
`docs://mcp-server/guides%2Furis.md`.

The listing at `docs://mcp-server` already emits the encoded form, so a client that
reads the listing first never has to apply this rule by hand.
