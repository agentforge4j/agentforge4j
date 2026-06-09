# Jira — issues, search, and comments as governed capabilities

> **Tier:** `MCP` — Atlassian publishes a hosted (remote) MCP server, so this is documented as an
> MCP server config + capability bindings (Tier 1). It is the **hosted / `streamable_http`**
> counterpart to the self-hosted [GitHub](../github/README.md) `stdio` recipe.

AgentForge4j connects to and governs the hosted Jira MCP server; it never builds, pulls, or runs it.
The operator points AgentForge4j at the hosted endpoint and completes the server's authorization.

## Capabilities

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `jira.search_issues` | Search issues (e.g. by JQL) | read-only | `ALLOW` |
| `jira.create_issue` | Create an issue | **mutating** | **`REQUIRE_APPROVAL`** |
| `jira.add_comment` | Comment on an issue | **mutating** | **`REQUIRE_APPROVAL`** |

## Transport

`streamable_http` — the server is hosted/remote, so AgentForge4j connects over HTTP. (The platform
transport layer supports `STDIO` and `STREAMABLE_HTTP`; if the vendor's hosted server is SSE-only,
see Caveats.)

## Config files

Copy the JSON files in this folder and fill in the placeholders, then apply them against the platform
MCP API (or the MCP admin UI). Secrets stay as `${secret:KEY}` references — never inline a token.

- [`mcp-server-config.json`](./mcp-server-config.json) — `POST /api/v1/mcp/servers`
  (`McpServerConfigRequest`). For a hosted server set `transport: "STREAMABLE_HTTP"`, point `url` at
  the vendor's endpoint (`<your-jira-host>`), and leave `command`/`envJson` null. `headersJson` is a
  JSON-string map of per-server request headers — here `Authorization` carries `${secret:JIRA_TOKEN}`
  (resolved to the full header value at connect; now first-class via the per-server headers support).
- [`capability-bindings.json`](./capability-bindings.json) — `POST /api/v1/mcp/bindings`, one object
  per capability. Replace `mcpServerConfigId` with the id returned when the server config is created;
  `workflowId: null` means tenant-wide.

> Authorization to a hosted Jira server is handled by the **server's own OAuth flow** (the operator
> grants access to their Atlassian site) where applicable; the `${secret:JIRA_TOKEN}` header is for a
> deployment that fronts the server with a static token. Substitute the real hosted endpoint and auth
> scheme from the vendor's docs (see Caveats).

## Secrets

| Secret-reference key | What it is |
|---|---|
| `JIRA_TOKEN` | The Authorization header value for the hosted server (e.g. a `Bearer …` token), resolved per tenant. Omit it entirely if the server authorizes purely via its own OAuth flow. |

Store any token as a secret reference, never plaintext.

## Operator run instructions

- **Hosted:** point `url` at the vendor's hosted MCP endpoint and complete the server's
  authorization (OAuth) for the target Atlassian site, or supply the `Authorization` header via
  `JIRA_TOKEN`. AgentForge4j connects to the endpoint; it does not run anything.

## Governance notes

- Gate the mutating capabilities with `REQUIRE_APPROVAL`:

  ```json
  [
    { "capability": "jira.create_issue", "decision": "REQUIRE_APPROVAL", "reason": "Creates a ticket in a real project." },
    { "capability": "jira.add_comment",  "decision": "REQUIRE_APPROVAL", "reason": "Posts a visible comment." }
  ]
  ```

- A hosted server needs no `stdio` sandbox, but scope the granted OAuth permissions (or the token) to
  the minimum the bound capabilities require.

## Caveats / version pins

- **Confirm the current hosted endpoint, transport, and authorization flow in Atlassian's docs.**
  Hosted MCP endpoints and auth schemes move; the `url` in `mcp-server-config.json` is a placeholder.
  If the hosted server is **SSE-only**, the platform's `STREAMABLE_HTTP` transport will not connect
  directly — front it with a streamable-HTTP bridge or use a self-hosted `stdio` variant.
- Verify the exact `remoteToolName` values against the server's live `tools/list`; Jira tool names
  and input schemas (e.g. JQL parameters) drift.
- Mind Atlassian API rate limits and the OAuth scopes granted to the server.
