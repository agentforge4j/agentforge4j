<!--
Canonical recipe skeleton. Copy this whole `_template/` folder to `docs/integrations/<vendor>/` and
fill every section in order. Keep the JSON files an operator copies (delete the ones that do not
apply: MCP tiers keep mcp-server-config.json + capability-bindings.json; the HTTP tier keeps
http-endpoints.json). Delete these HTML comments and any section marked "(MCP tier only)" /
"(HTTP tier only)" that does not apply. See ../CONVENTIONS.md for the tier decision rule, the
capability-naming rule, and the secret / no-orchestration rules.
-->

# <Vendor> — <one-line summary of what it unlocks>

> **Tier:** `MCP` | `HTTP` | `NATIVE` — _one-line justification (why this tier per CONVENTIONS.md §1)._

AgentForge4j connects to and governs this integration; it never builds, pulls, or runs the
server. The operator runs the server (or points at a hosted endpoint); this recipe tells them how.

## Capabilities

Capability IDs are `<domain>.<verb_object>` (lowercase snake_case — see ../CONVENTIONS.md §2).

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `<domain>.<verb_object>` | _what it does_ | read-only \| **mutating** | `ALLOW` \| **`REQUIRE_APPROVAL`** |

> Mutating capabilities default to a `REQUIRE_APPROVAL` recommendation (see Governance notes).

## Transport _(MCP tier only)_

`stdio` | `streamable_http` — _per CONVENTIONS.md §2: local subprocess/desktop → `stdio`;
remote/hosted → `streamable_http`._

## Config files

Copy the JSON file(s) in this folder, fill in every placeholder, and apply them. Secrets stay as
`${secret:KEY}` references (MCP `envJson`/`headersJson`) or secret-reference keys (HTTP
`secretHeaders`) — never inline a credential.

**(MCP tier)**
- [`mcp-server-config.json`](./mcp-server-config.json) — `POST /api/v1/mcp/servers`
  (`McpServerConfigRequest`). `stdio` uses `command` + `envJson`; `streamable_http` uses `url` +
  `headersJson`. Null the fields that do not apply to the chosen transport.
- [`capability-bindings.json`](./capability-bindings.json) — `POST /api/v1/mcp/bindings`, one object
  per capability. Set `mcpServerConfigId` to the id returned when the server config is created;
  `workflowId: null` = tenant-wide.

**(HTTP tier)**
- [`http-endpoints.json`](./http-endpoints.json) — an array of `HttpEndpointDefinition`-shaped
  objects (one per capability) translated into a code-defined `HttpToolProvider`. `secretHeaders`
  maps a header to a secret-reference key resolved at invoke.

## Secrets

| Secret-reference key | What it is |
|---|---|
| `SECRET_KEY` | _e.g. a personal access token with scopes X, Y_ |

Store every credential as a **secret reference**, never plaintext.

## Operator run instructions

_How to start the server — reference docs, not executed by AgentForge4j:_

- **Bare binary:** `<command>`
- **Container:** `docker run -i --rm -e <ENV_VAR> <image>` _(the `-i` keeps stdio open for an MCP
  stdio server; AgentForge4j connects to it, it does not run it)_
- **Hosted:** point `url` at `<https endpoint>`; authenticate per the vendor's scheme.

## Governance notes

- Recommended `tool_policy_rule` posture: gate every **mutating** capability with
  `{"capability": "<domain>.<verb_object>", "decision": "REQUIRE_APPROVAL", "reason": "..."}`.
  Read-only capabilities may be `ALLOW`.
- **stdio sandbox note:** an MCP `stdio` server is a local subprocess started by the operator —
  apply explicit per-server opt-in and sandboxing (a container is one option).

## Caveats / version pins

- _Confirm the server package/image/endpoint and the exact `remoteToolName` values against the
  vendor's current docs and the server's `tools/list` — MCP tool names and schemas drift._
- _Known rate limits, required auth scopes, schema-drift notes._
