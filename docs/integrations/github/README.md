# GitHub — repositories, pull requests, and issues as governed capabilities

> **Tier:** `MCP` — GitHub publishes an official MCP server, so this is documented as an MCP server
> config + capability bindings (Tier 1, the default).

AgentForge4j connects to and governs the GitHub MCP server; it never builds, pulls, or runs it. The
operator runs the server (a local subprocess) and AgentForge4j connects over `stdio`.

## Capabilities

Capability IDs are `<domain>.<verb_object>` (lowercase snake_case).

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `github.search_repositories` | Search repositories by query | read-only | `ALLOW` |
| `github.get_pull_request` | Fetch a pull request by number | read-only | `ALLOW` |
| `github.create_pull_request` | Open a pull request | **mutating** | **`REQUIRE_APPROVAL`** |
| `github.create_issue` | Create an issue | **mutating** | **`REQUIRE_APPROVAL`** |

## Transport

`stdio` — the GitHub MCP server runs as a local subprocess (bare binary or `docker run -i`).

## Config files

Copy the JSON files in this folder and fill in the placeholders, then apply them against the platform
MCP API (or the MCP admin UI). Secrets stay as `${secret:KEY}` references — never inline a token.

- [`mcp-server-config.json`](./mcp-server-config.json) — `POST /api/v1/mcp/servers`
  (`McpServerConfigRequest`). For `stdio`, `command` is the operator's run method (the
  `docker run -i …` shown, or a bare binary) and `envJson` is a JSON-string map carrying the token as
  `${secret:GITHUB_TOKEN}` under the env var the server reads (`GITHUB_PERSONAL_ACCESS_TOKEN`);
  `url` is `null` and no `headersJson` is used.
- [`capability-bindings.json`](./capability-bindings.json) — `POST /api/v1/mcp/bindings`, one object
  per capability. Replace `mcpServerConfigId` with the id returned when the server config is created;
  `workflowId: null` means tenant-wide (set a workflow id to scope a binding to one workflow).

## Secrets

| Secret-reference key | What it is |
|---|---|
| `GITHUB_TOKEN` | A GitHub Personal Access Token. Grant the **least** scopes the bound capabilities need (e.g. `repo` for PR/issue writes; read-only scopes if only searching). |

Store the token as a secret reference, never plaintext.

## Operator run instructions

The operator runs the server; AgentForge4j connects to it.

- **Container (stdio):** `docker run -i --rm -e GITHUB_PERSONAL_ACCESS_TOKEN ghcr.io/github/github-mcp-server`
  — the `-i` keeps stdio open for the MCP connection.
- **Bare binary:** download the `github-mcp-server` release for your platform and run it with
  `GITHUB_PERSONAL_ACCESS_TOKEN` set in the environment.

AgentForge4j does not pull the image or start the process — these are operator instructions.

## Governance notes

- Gate the mutating capabilities with `REQUIRE_APPROVAL`:

  ```json
  [
    { "capability": "github.create_pull_request", "decision": "REQUIRE_APPROVAL", "reason": "Opens a PR against a real repository." },
    { "capability": "github.create_issue",        "decision": "REQUIRE_APPROVAL", "reason": "Creates a public/private issue." }
  ]
  ```

  The read-only capabilities may stay `ALLOW`.
- **stdio sandbox note:** this server is a local subprocess started by the operator. Apply explicit
  per-server opt-in and sandboxing (a container is one option).

## Caveats / version pins

- Confirm the current image/binary and the exact `remoteToolName` values against the GitHub MCP
  server's docs and its live `tools/list` — tool names and input schemas drift between releases.
- GitHub also offers a **hosted remote** MCP server; to use that instead, switch `mcp-server-config.json`
  to `transport: "STREAMABLE_HTTP"` with the hosted `url` (see the [Jira](../jira/README.md) recipe for
  the hosted shape). Pin to a server version where practical.
- Mind GitHub API rate limits and token scope; over-scoped tokens widen blast radius.
