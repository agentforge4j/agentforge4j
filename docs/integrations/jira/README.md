# Jira — issues, search, and comments as governed capabilities

> **Tier:** `MCP_STREAMABLE_HTTP` — Atlassian publishes a hosted (remote) MCP server, so this is
> documented as an `IntegrationDefinition` of type `MCP_STREAMABLE_HTTP` (Tier 1). It is the
> **hosted** counterpart to the self-hosted [GitHub](../github/README.md) `MCP_STDIO` recipe.

AgentForge4j connects to and governs the hosted Jira MCP server; it never builds, pulls, or runs it.
The operator points AgentForge4j at the hosted endpoint and completes the server's authorization.

## Capabilities

The runtime capability for an MCP-sourced tool is the vendor server's advertised tool name — no
`<domain>.` prefix is added by AgentForge4j (see [`CONVENTIONS.md`](../CONVENTIONS.md) §2). The
names below are illustrative; confirm them against the Jira MCP server's live `tools/list` before
relying on them.

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `search_issues` | Search issues (e.g. by JQL) | read-only | `Allow` |
| `create_issue` | Create an issue | **mutating** | **`RequireApproval`** |
| `add_comment` | Comment on an issue | **mutating** | **`RequireApproval`** |

## Transport

`MCP_STREAMABLE_HTTP` — the server is hosted/remote, so AgentForge4j connects over HTTP. If the
vendor's hosted server is SSE-only, see Caveats.

## Config file

Copy [`jira.json`](./jira.json), fill in the placeholder `url`, and drop it into the directory
passed to `AgentForge4jBootstrap.withIntegrationsDir(...)`.

> **Current OSS gap:** the file-loaded `MCP_STREAMABLE_HTTP` config carries **no header field**
> today — there is no way to attach a static `Authorization` header through this path. If the
> hosted server authorizes purely via its own OAuth flow (the operator grants access to their
> Atlassian site out of band), that's fine — this recipe needs nothing further. If the deployment
> instead fronts the server with a static token header, you must construct
> `StreamableHttpTransport` directly in Java (which does accept `secretHeaders` + a
> `SecretResolver`) and register it via `AgentForge4jBootstrap.withToolProviders(...)` instead of
> the file-loaded path — see [`CONVENTIONS.md`](../CONVENTIONS.md) §3.

## Secrets

| Secret-reference key | What it is |
|---|---|
| `JIRA_TOKEN` | The Authorization header value for the hosted server (e.g. a `Bearer …` token), only needed if you take the direct-Java path above. Omit entirely if the server authorizes purely via its own OAuth flow. |

Never inline a token in `jira.json` — there is nowhere in its schema to put one today (see the gap
note above), which is itself the safest default: don't invent a field the loader won't consume.

## Operator run instructions

- **Hosted:** point `url` at the vendor's hosted MCP endpoint and complete the server's
  authorization (OAuth) for the target Atlassian site, or take the direct-Java path above if the
  deployment needs a static `Authorization` header. AgentForge4j connects to the endpoint; it does
  not run anything.

## Governance notes

- Gate the mutating capabilities by returning `PolicyDecision.RequireApproval(reason,
  approverScope)` for them from your own `ToolPolicy` (`AgentForge4jBootstrap.withToolPolicy(...)`):
  `create_issue` ("Creates a ticket in a real project.") and `add_comment` ("Posts a visible
  comment."). OSS ships no default per-capability rule table — see
  [`CONVENTIONS.md`](../CONVENTIONS.md) §3.
- A hosted server needs no stdio sandbox, but scope the granted OAuth permissions (or the token) to
  the minimum the used capabilities require.

## Caveats / version pins

- **Confirm the current hosted endpoint, transport, and authorization flow in Atlassian's docs.**
  Hosted MCP endpoints and auth schemes move; the `url` in `jira.json` is a placeholder. If the
  hosted server is **SSE-only**, `MCP_STREAMABLE_HTTP` will not connect directly — front it with a
  streamable-HTTP bridge or use a self-hosted `MCP_STDIO` variant.
- Verify the exact advertised tool names against the server's live `tools/list`; Jira tool names
  and input schemas (e.g. JQL parameters) drift.
- Mind Atlassian API rate limits and the OAuth scopes granted to the server.
