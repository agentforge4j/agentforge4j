# Integration recipe conventions

How to author a recipe under `docs/integrations/`. A **recipe** turns an external service into a
**governed AgentForge4j capability**: it is documentation (a markdown page + sample config +
secret-reference list + operator run instructions), **not** a shipped image, a Java module, or
anything AgentForge4j executes. AgentForge4j connects to and governs servers; it never builds,
pulls, or runs them.

Each vendor is a **folder** (`docs/integrations/<vendor>/`) holding the recipe `README.md` plus the
JSON config file(s) an operator copies and fills in. Copy the [`_template/`](./_template/) folder to
start a new vendor; keep the `README.md` section order fixed.

---

## §1 — Tier decision rule (binding)

Each recipe states which tier applies and why:

1. **MCP recipe (default).** An official/maintained MCP server exists → document its
   `mcp_server_config` + capability bindings. Preferred for every vendor that has one.
2. **HTTP-tool recipe (fallback).** No MCP server, but a stable REST API → document a code-defined
   `HttpToolProvider` (`agentforge4j-tools-http`) with one `HttpEndpointDefinition` per capability.
   Governed identically (schema, policy, audit, retry/timeout).
3. **Native `ToolProvider` (exception).** Neither fits and the integration is high-value/complex
   (e.g. FHIR) → a Java module. **Out of scope for these docs** — each native provider is its own
   phased code design. A vendor earns Tier 3 only when **all** hold: (a) no adequate MCP server and
   the REST surface is too rich/stateful for flat `HttpEndpointDefinition`s; (b) domain semantics
   need typed handling; (c) auth is non-trivial (e.g. SMART-on-FHIR/OAuth the secret resolver can't
   cover).

Tiers 1 and 2 are documentation only. Tier 3 is code and is not produced here.

---

## §2 — Conventions (check every recipe against these)

- **Capability naming.** `<domain>.<verb_object>`, lowercase snake_case. `domain` is a logical area
  (`github`, `jira`, `postgres`, `slack`, `filesystem`), **never** a vendor brand-version. This
  matches the core tool-SPI convention (`ToolDescriptor.capability`,
  e.g. `github.create_pull_request`).
- **Transport (MCP).** Local subprocess / desktop → `stdio`; remote / hosted → `streamable_http`.
  The platform transport layer supports `STDIO` and `STREAMABLE_HTTP` only; an SSE-only legacy
  server needs a bridge — note it as a caveat.
- **Secrets.** Only secret-*reference* keys appear in any sample — `${secret:KEY}` in an MCP
  `envJson`, or a resolver key in an HTTP `secretHeaders` map. **No plaintext credential, token, or
  key in any recipe**, and no secret routed through tool arguments.
- **No orchestration claims.** Recipes must not imply AgentForge4j runs or pulls containers. Any
  `docker run` line is an **operator instruction**, not something the framework does.
- **Mutating capabilities** carry a documented `REQUIRE_APPROVAL` recommendation.

These are enforceable by review (and by an optional lint, if/when a CI markdown-lint hook is added —
none exists today). Concretely, a recipe fails review if: a capability id is not lowercase
`<domain>.<verb_object>`; a sample contains a string that looks like a real credential rather than a
`${secret:...}` reference or resolver key; or it claims AgentForge4j runs/pulls a server.

---

## §3 — Grounded config shapes (use these exact field names)

Recipe samples must match the live platform persistence and the OSS HTTP-tool surface. Do **not**
invent field names. (Sources: platform `V109__create_mcp_tables.sql` + `V110__add_mcp_server_headers.sql`
+ the `…mcp.dto` request DTOs; OSS `agentforge4j-tools-http`.)

### MCP server config — `POST /api/v1/mcp/servers` (`McpServerConfigRequest`)

| Field | Type | Notes |
|---|---|---|
| `name` | string | unique within tenant |
| `providerId` | string | stable provider id, e.g. `mcp:github` |
| `transport` | string | `STDIO` or `STREAMABLE_HTTP` |
| `command` | string \| null | `STDIO` only — executable + args (whitespace-tokenized) |
| `url` | string \| null | `STREAMABLE_HTTP` only — hosted endpoint |
| `envJson` | string \| null | JSON-string map of env vars; secrets as `${secret:KEY}` |
| `headersJson` | string \| null | `STREAMABLE_HTTP` per-server request headers; JSON-string map; secrets as `${secret:KEY}` |
| `enabled` | boolean | |

> `envJson` carries process env for `stdio` servers. For `streamable_http` the platform connects to
> `url` with a request timeout and attaches `headersJson` as per-server request headers (e.g.
> `Authorization`). In both maps a value of exactly `${secret:KEY}` is a secret reference resolved
> per tenant at connect; any other value is a literal. Note hosted-server OAuth in Caveats where it
> applies.

### Capability binding — `POST /api/v1/mcp/bindings` (`ToolCapabilityBindingRequest`)

| Field | Type | Notes |
|---|---|---|
| `capability` | string | `<domain>.<verb_object>` |
| `mcpServerConfigId` | string | id of the server config |
| `remoteToolName` | string | the server's advertised tool name |
| `workflowId` | string \| null | null ⇒ tenant-wide; set ⇒ workflow-scoped (workflow binding wins) |
| `enabled` | boolean | |

### Tool policy rule — `tool_policy_rule`

| Field | Type | Notes |
|---|---|---|
| `capability` | string | a `<domain>.<verb_object>` id, or `*` wildcard |
| `decision` | string | `ALLOW` \| `REQUIRE_APPROVAL` \| `DENY` |
| `minRole` | string \| null | persisted; RBAC enforcement deferred |
| `reason` | string \| null | surfaced to the operator on deny/approval |

Default (no rule) is `ALLOW`. Recommend `REQUIRE_APPROVAL` for mutating capabilities.

### HTTP-tool — `HttpEndpointDefinition` (OSS `agentforge4j-tools-http`)

Record components, in order: `capability`, `displayName`, `description`, `method`
(`HttpMethod` GET/POST/PUT/PATCH/DELETE/HEAD), `urlTemplate` (absolute `http`/`https` with `{name}`
placeholders), `inputSchema` (`JsonNode`, non-null JSON Schema), `outputSchema` (`JsonNode` \| null),
`queryArgs` (`Set<String>`), `bodyMode` (`NONE`/`JSON`), `staticHeaders` (`Map<String,String>`),
`secretHeaders` (`Map<String,String>` header → **secret-reference key**, resolved at invoke),
`timeout` (`Duration` \| null), `maxRetries` (`Integer` \| null, null = unset), `retryNonIdempotent`
(`boolean`), `maxResponseBytes` (`Long` \| null). Construct a `HttpToolProvider(name, definitions,
secretResolver, httpClient, defaultOptions, defaultMaxResponseBytes)` and pass it to
`AgentForge4jBootstrap.defaults().withToolProviders(...)`.

---

## §4 — Where recipes live

```
docs/integrations/
├── README.md            # index table (vendor · tier · transport · capabilities · status)
├── CONVENTIONS.md       # this file
├── _template/           # canonical skeleton — copy to <vendor>/
│   ├── README.md
│   ├── mcp-server-config.json
│   ├── capability-bindings.json
│   └── http-endpoints.json
└── <vendor>/            # one folder per integration
    ├── README.md            # the recipe (section order fixed)
    ├── mcp-server-config.json   # MCP tier
    ├── capability-bindings.json # MCP tier
    └── http-endpoints.json      # HTTP tier
```

- A `<vendor>/README.md` follows the section order in [`_template/README.md`](./_template/README.md);
  its "Config files" section points to the folder's JSON file(s) and explains each placeholder — it
  does not re-inline the full JSON.
- The JSON file(s) are what an operator copies and applies (MCP: against the platform MCP API/UI;
  HTTP: translated into a code-defined `HttpToolProvider`). Keep only the files for the chosen tier.
- [`README.md`](./README.md) is the index table linking each `<vendor>/` folder.

Per-vendor facts (server id/package, capabilities, secret refs, run method) are **owner-supplied
input**, not invented. A vendor fact a recipe needs but lacks is a **content gap** — request it; do
not fabricate server ids, capabilities, or env vars.
