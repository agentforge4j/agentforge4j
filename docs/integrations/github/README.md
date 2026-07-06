# GitHub — repositories, pull requests, and issues as governed capabilities

> **Tier:** `MCP_STDIO` — GitHub publishes an official MCP server, so this is documented as an
> `IntegrationDefinition` of type `MCP_STDIO` (Tier 1, the default).

AgentForge4j connects to and governs the GitHub MCP server; it never builds, pulls, or runs it. The
operator runs the server (a local subprocess) and AgentForge4j connects over stdio.

## Capabilities

The runtime capability for an MCP-sourced tool is the vendor server's advertised tool name — no
`<domain>.` prefix is added by AgentForge4j (see [`CONVENTIONS.md`](../CONVENTIONS.md) §2). The
names below are illustrative; confirm them against the GitHub MCP server's live `tools/list` before
relying on them.

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `search_repositories` | Search repositories by query | read-only | `Allow` |
| `get_pull_request` | Fetch a pull request by number | read-only | `Allow` |
| `create_pull_request` | Open a pull request | **mutating** | **`RequireApproval`** |
| `create_issue` | Create an issue | **mutating** | **`RequireApproval`** |

## Transport

`MCP_STDIO` — the GitHub MCP server runs as a local subprocess (bare binary or `docker run -i`).

## Config file

Copy [`github.json`](./github.json), fill in any placeholders, and drop it into the directory
passed to `AgentForge4jBootstrap.withIntegrationsDir(...)`. `command`/`args` are the executable and
its arguments **as separate fields** — no shell parsing happens, so `docker run -i --rm ...` is
split into `"command": "docker"` and an `args` array, not one string.

The file intentionally carries **no `env` entry** for the token: `docker run -e
GITHUB_PERSONAL_ACCESS_TOKEN` (the flag with no `=value`) forwards that variable from the
environment `docker` itself runs in — i.e. wherever the AgentForge4j process runs — into the
container, so the credential never has to be written into this JSON file or resolved by
AgentForge4j's own code. See Secrets below.

## Secrets

| Secret-reference key | What it is |
|---|---|
| `GITHUB_PERSONAL_ACCESS_TOKEN` | A GitHub Personal Access Token. Grant the **least** scopes the used capabilities need (e.g. `repo` for PR/issue writes; read-only scopes if only searching). In this recipe it is the OS environment variable forwarded into the container (see below); on the direct-Java path it is the reference key you pass to `SecretResolver.resolve(key)`. |

Set `GITHUB_PERSONAL_ACCESS_TOKEN` in the OS environment where the AgentForge4j process (and
therefore the `docker` subprocess it launches) runs — sourced from your own secret store (e.g. a
vault-injected environment, a systemd `EnvironmentFile`, or your container platform's secret
mechanism) — never committed to source control or written into `github.json`. If you need
AgentForge4j itself to resolve the token from a `SecretResolver` before launching the process
(rather than relying on host-environment forwarding), construct `StdioTransport` directly in Java
and register it via `AgentForge4jBootstrap.withToolProviders(...)` instead of the file-loaded path
— see [`CONVENTIONS.md`](../CONVENTIONS.md) §3.

## Operator run instructions

The operator runs the server; AgentForge4j connects to it.

- **Container (stdio):** `docker run -i --rm -e GITHUB_PERSONAL_ACCESS_TOKEN ghcr.io/github/github-mcp-server`
  — the `-i` keeps stdio open for the MCP connection.
- **Bare binary:** download the `github-mcp-server` release for your platform and run it with
  `GITHUB_PERSONAL_ACCESS_TOKEN` set in the environment.

AgentForge4j does not pull the image or start the process — these are operator instructions.

## Governance notes

- Gate the mutating capabilities by returning `PolicyDecision.RequireApproval(reason,
  approverScope)` for them from your own `ToolPolicy` (`AgentForge4jBootstrap.withToolPolicy(...)`):
  `create_pull_request` ("Opens a PR against a real repository.") and `create_issue` ("Creates a
  public/private issue."). The read-only capabilities may return `Allow()`. OSS ships no default
  per-capability rule table — see [`CONVENTIONS.md`](../CONVENTIONS.md) §3.
- **stdio sandbox note:** this server is a local subprocess started by the operator. Apply explicit
  per-server opt-in and sandboxing (a container is one option).

## Caveats / version pins

- Confirm the current image/binary and the exact advertised tool names against the GitHub MCP
  server's docs and its live `tools/list` — tool names and input schemas drift between releases.
- GitHub also offers a **hosted remote** MCP server; to use that instead, switch to
  `MCP_STREAMABLE_HTTP` with the hosted `url` (see the [Jira](../jira/README.md) recipe for the
  hosted shape, including its current header-support gap). Pin to a server version where practical.
- Mind GitHub API rate limits and token scope; over-scoped tokens widen blast radius.
