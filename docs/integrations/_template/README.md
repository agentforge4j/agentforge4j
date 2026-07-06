<!--
Canonical recipe skeleton. Copy this whole `_template/` folder to `docs/integrations/<vendor>/` and
fill every section in order. Keep only the one integration-definition JSON file for the chosen tier
(delete the other two example files and rename the one you keep to `<vendor-id>.json`). Delete
these HTML comments and any section marked "(MCP tier only)" / "(HTTP tier only)" that does not
apply. See ../CONVENTIONS.md for the tier decision rule, the capability-naming rule, and the
secret / no-orchestration rules.
-->

# <Vendor> — <one-line summary of what it unlocks>

> **Tier:** `MCP_STDIO` | `MCP_STREAMABLE_HTTP` | `HTTP_TOOL` — _one-line justification (why this
> tier per CONVENTIONS.md §1)._

AgentForge4j connects to and governs this integration; it never builds, pulls, or runs the
server. The operator runs the server (or points at a hosted endpoint); this recipe tells them how.

## Capabilities

For `HTTP_TOOL`, capability IDs are author-defined and schema-enforced as `<domain>.<verb_object>`
(lowercase snake_case). For MCP, the capability **is the vendor server's advertised tool name** —
confirm it against the server's live `tools/list` rather than assuming it (see ../CONVENTIONS.md §2).

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `<capability>` | _what it does_ | read-only \| **mutating** | `Allow` \| **`RequireApproval`** |

> Mutating capabilities default to a `RequireApproval` recommendation (see Governance notes).

## Transport _(MCP tier only)_

`MCP_STDIO` | `MCP_STREAMABLE_HTTP` — _per CONVENTIONS.md §2: local subprocess/desktop →
`MCP_STDIO`; remote/hosted → `MCP_STREAMABLE_HTTP`._

## Config file

Copy the one JSON file in this folder that matches the chosen tier, fill in every placeholder, and
drop it into the directory passed to `AgentForge4jBootstrap.withIntegrationsDir(...)` (or the
`agentforge4j.integrations.dir` config key). Secrets are always a **bare secret-reference key**
(e.g. `GITHUB_TOKEN`), resolved via `SecretResolver.resolve(key)` — never a `${...}`-wrapped
template, and never inlined as a literal credential.

**(`MCP_STDIO` tier)**
- [`mcp-stdio.integration.json`](./mcp-stdio.integration.json) — `command` is the executable only
  (no shell parsing); split a `docker run ...` invocation into `command: "docker"` +
  `args: ["run", ...]`. `env` values are **literal today** — there is no secret-reference
  resolution for MCP env vars in the file-loaded path (see Secrets below).

**(`MCP_STREAMABLE_HTTP` tier)**
- [`mcp-streamable-http.integration.json`](./mcp-streamable-http.integration.json) — `url` is the
  hosted endpoint. **No per-server headers field exists in this path today** — see Secrets below if
  the server needs a static `Authorization` header.

**(`HTTP_TOOL` tier)**
- [`http-tool.integration.json`](./http-tool.integration.json) — `config` is an array of
  `HttpEndpointDefinition`-shaped objects (one per capability). `secretHeaders` maps a header to a
  bare secret-reference key resolved at invoke.

## Secrets

| Secret-reference key | What it is |
|---|---|
| `SECRET_KEY` | _e.g. a personal access token with scopes X, Y_ |

Store every credential as a **secret reference**, never plaintext.

> **MCP current gaps (state which applies, if any):**
> - `MCP_STDIO` file-loaded `env` values are literal — do not put a real secret in the JSON file.
>   Either rely on the credential already being present in the OS/parent process environment where
>   AgentForge4j runs (confirm your MCP SDK version's stdio launcher inherits unset keys from the
>   parent environment), or construct `StdioTransport` directly in Java, resolving the secret
>   yourself via `SecretResolver.resolve(key)` before building the `env` map, and register it via
>   `AgentForge4jBootstrap.withToolProviders(...)` instead of the file-loaded path.
> - `MCP_STREAMABLE_HTTP` file-loaded config carries no header field at all — a static
>   `Authorization` header is only supported by constructing `StreamableHttpTransport` directly in
>   Java (which does accept `secretHeaders` + a `SecretResolver`).

## Operator run instructions

_How to start the server — reference docs, not executed by AgentForge4j:_

- **Bare binary:** `<command>` `<args...>`
- **Container:** `docker run -i --rm -e <ENV_VAR> <image>` _(the `-i` keeps stdio open for an MCP
  stdio server; AgentForge4j connects to it, it does not run it — split this into `command`/`args`
  as shown in the config file, not one string)_
- **Hosted:** point `url` at `<https endpoint>`; authenticate per the vendor's scheme.

## Governance notes

- Recommended policy posture: an embedder-authored `ToolPolicy`
  (`AgentForge4jBootstrap.withToolPolicy(...)`) should return `PolicyDecision.RequireApproval(reason,
  approverScope)` for every **mutating** capability listed above, and `Allow()`/`Deny(reason)`
  otherwise. OSS ships no default per-capability rule table — the shipped
  `SecureDefaultToolPolicy` gates by transport kind only (denies every MCP/HTTP integration until
  you opt in with `ToolPolicy.allowAll()` or a custom policy).
- **stdio sandbox note:** an MCP `MCP_STDIO` server is a local subprocess started by the operator —
  apply explicit per-server opt-in and sandboxing (a container is one option).

## Caveats / version pins

- _Confirm the server package/image/endpoint and the exact advertised tool names against the
  vendor's current docs and the server's live `tools/list` — MCP tool names and schemas drift._
- _Known rate limits, required auth scopes, schema-drift notes._
