# Integration recipe conventions

How to author a recipe under `docs/integrations/`. A **recipe** turns an external service into a
**governed AgentForge4j capability**: it is documentation (a markdown page + one integration
definition file + secret-reference list + operator run instructions), **not** a shipped image, a
Java module, or anything AgentForge4j executes. AgentForge4j connects to and governs servers; it
never builds, pulls, or runs them.

Each vendor is a **folder** (`docs/integrations/<vendor>/`) holding the recipe `README.md` plus the
one integration-definition JSON file an operator drops into their `agentforge4j.integrations.dir`.
Copy the [`_template/`](./_template/) folder to start a new vendor; keep the `README.md` section
order fixed.

---

## §1 — Tier decision rule (binding)

Each recipe states which tier applies and why:

1. **MCP recipe (default).** An official/maintained MCP server exists → document an
   `IntegrationDefinition` of type `MCP_STDIO` (local subprocess) or `MCP_STREAMABLE_HTTP` (hosted).
   Preferred for every vendor that has one.
2. **HTTP recipe (fallback).** No MCP server, but a stable REST API → document an
   `IntegrationDefinition` of type `HTTP_TOOL` (`agentforge4j-tools-http`), one
   `HttpEndpointDefinition` per capability. Governed identically (schema, policy, audit, retry/
   timeout).
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
  (`github`, `jira`, `postgres`, `slack`, `filesystem`), **never** a vendor brand-version. This is
  schema-enforced for `HTTP_TOOL` (`^[a-z0-9_]+\.[a-z0-9_]+$` in `integration.schema.json`).
  **For MCP, the runtime capability is whatever string the vendor's server reports from
  `tools/list`** — `McpToolProvider` uses the remote tool's literal name as the capability, with no
  renaming/namespacing layer. A recipe must verify each vendor's actual advertised tool names rather
  than assume they already look like `<domain>.<verb_object>`; the capability table documents what
  the server *actually* reports.
- **Transport (MCP).** Local subprocess / desktop → `MCP_STDIO`; remote / hosted →
  `MCP_STREAMABLE_HTTP`. An SSE-only legacy server needs a bridge — note it as a caveat.
- **Secrets.** Only secret-*reference* keys appear in any sample — a bare key (e.g. `GITHUB_TOKEN`)
  in an `env` object (`MCP_STDIO`) or a `secretHeaders` map value (`HTTP_TOOL`), resolved via
  `SecretResolver.resolve(key)` at connect/invoke. **No plaintext credential, token, or key in any
  recipe**, and no secret routed through tool arguments. See §3 — the reference is the literal key
  string, not a `${...}`-wrapped template.
- **No orchestration claims.** Recipes must not imply AgentForge4j runs or pulls containers. Any
  `docker run` line is an **operator instruction**, not something the framework does.
- **Mutating capabilities** carry a documented `RequireApproval` recommendation for the embedder's
  own `ToolPolicy` (see §3).

These are enforceable by review (and by an optional lint, if/when a CI markdown-lint hook is added —
none exists today). Concretely, a recipe fails review if: a capability id is not lowercase
`<domain>.<verb_object>` (for `HTTP_TOOL`; for MCP, if it hasn't been verified against the vendor's
real `tools/list`); a sample contains a string that looks like a real credential rather than a bare
secret-reference key; or it claims AgentForge4j runs/pulls a server.

---

## §3 — Grounded config shapes (use these exact field names)

Recipe samples must match the live OSS integration-loading surface. Do **not** invent field names,
and do not reintroduce a `${secret:KEY}`-style template — OSS resolves secrets by passing the bare
reference key straight to `SecretResolver.resolve(key)`.

### The integration-definition envelope (`IntegrationDefinition` / `integration.schema.json`)

Every recipe is **one JSON file**, loaded by `FileSystemIntegrationConfigLoader` from the directory
passed to `AgentForge4jBootstrap.withIntegrationsDir(Path)` (or the
`agentforge4j.integrations.dir` config key). The filename stem becomes the integration id when `id`
is omitted.

| Field | Type | Notes |
|---|---|---|
| `id` | string, optional | falls back to the filename stem |
| `displayName` | string | required, human-readable |
| `type` | string | required — `MCP_STDIO`, `MCP_STREAMABLE_HTTP`, or `HTTP_TOOL` |
| `active` | boolean | optional, defaults to `true` |
| `config` | object (MCP) or array (HTTP_TOOL) | type-specific payload, see below |

An integration declares **no separate capability-binding file**: the tools it exposes are exactly
the realised set the provider reports (`ToolProvider.listTools()`), and capability resolution keys
off those alone. A capability id colliding with another integration or a pre-built `ToolProvider`
fails the whole bootstrap fast, naming both sources.

### `MCP_STDIO` — `config` object

| Field | Type | Notes |
|---|---|---|
| `command` | string | required — the executable only, **not** a full shell command line |
| `args` | array of string | optional — command-line arguments, launched without shell interpretation |
| `env` | object of string | optional — literal environment variable values only |
| `requestTimeout` | string | optional, ISO-8601 duration (e.g. `PT30S`); defaults to 30s |

> `command`/`args` are passed to the process launcher exactly as given (no shell parsing) — a
> command like `docker run -i --rm ...` must be split into `"command": "docker"` and
> `"args": ["run", "-i", "--rm", ...]`, not one string. `env` values are literal today; there is no
> secret-reference resolution for MCP env vars in the file-loaded path — see the Secrets note below.

### `MCP_STREAMABLE_HTTP` — `config` object

| Field | Type | Notes |
|---|---|---|
| `url` | string | required — the hosted MCP endpoint; egress-checked before connect |
| `requestTimeout` | string | optional, ISO-8601 duration; defaults to 30s |
| `staticHeaders` | object of string | optional — literal header name to value, sent on every request (e.g. an API-version header) |
| `secretHeaders` | object of string | optional — header name to **bare secret-reference key**, resolved via `SecretResolver.resolve(key)` at connect time (e.g. `Authorization`) |

> A header name must not appear in both `staticHeaders` and `secretHeaders`, and header names are
> compared case-insensitively (HTTP header names are case-insensitive) — a duplicate under either
> rule fails fast at wiring time. If the hosted server authorizes purely via its own OAuth flow, omit
> both fields entirely — that's the safest default.

### `HTTP_TOOL` — `config` array of `HttpEndpointDefinition`

One object per capability. Record components, in order: `capability`, `displayName`, `description`,
`mutating` (`Boolean`; required in the file schema — on the programmatic path a `null` normalises
to `true`, conservative by default), `method`
(`HttpMethod`: `GET`/`POST`/`PUT`/`PATCH`/`DELETE`/`HEAD`), `urlTemplate` (absolute `http`/`https`
with `{name}` placeholders; host/port fixed, no placeholders before the path), `inputSchema`
(`JsonNode`, non-null JSON Schema), `outputSchema` (`JsonNode` | null), `queryArgs`
(`Set<String>`), `bodyMode` (`NONE`/`JSON`), `staticHeaders` (`Map<String,String>`, literal),
`secretHeaders` (`Map<String,String>` header → **bare secret-reference key**, resolved via
`SecretResolver.resolve(key)` at invoke), `timeout` (ISO-8601 duration | null), `maxRetries` (`int`,
`-1` = unset), `retryNonIdempotent` (`boolean`), `maxResponseBytes` (`Long` | null).

Loaded through the file-based path (`IntegrationType.HTTP_TOOL`, realised by
`HttpToolProviderFactory`), or constructed directly:
`new HttpToolProvider(configuredName, definitions, secretResolver, httpClient, egressGuard,
defaultOptions, defaultMaxResponseBytes, objectMapper)`, registered via
`AgentForge4jBootstrap.withToolProviders(...)`. The factory-built client always uses
`Redirect.NEVER`; a hand-built `HttpClient` must set this too, or a redirect to a private/metadata
host bypasses the egress guard (which validates only the originally mapped URL).

### Tool policy — embedder-authored `ToolPolicy`, not a rule file

OSS ships no persisted, capability-keyed policy-rule table. The default,
`SecureDefaultToolPolicy`, gates purely by `ToolSourceKind` (allows in-process tools, denies
`REMOTE_HTTP` and `LOCAL_PROCESS` — i.e. **every MCP and HTTP integration is denied until you opt
in**). To gate individual capabilities (e.g. "allow reads, require approval for writes"), supply a
custom `ToolPolicy` via `AgentForge4jBootstrap.withToolPolicy(...)` that inspects
`descriptor.capability()` and returns one of:

- `PolicyDecision.Allow()` — proceed.
- `PolicyDecision.RequireApproval(reason, approverScope)` — suspend for human approval;
  `approverScope` may be `null` (persisted as data, not enforced in OSS).
- `PolicyDecision.Deny(reason)` — reject outright.

There is no shipped reference implementation of a capability-list policy — this is a small amount
of code the embedding application owns. A recipe's Governance-notes section states the *recommended*
decision per capability; it is not a config file the operator applies verbatim.

---

## §4 — Where recipes live

```
docs/integrations/
├── README.md            # index table (vendor · tier · transport · capabilities · status)
├── CONVENTIONS.md       # this file
├── _template/           # canonical skeleton — copy to <vendor>/
│   ├── README.md
│   ├── mcp-stdio.integration.json          # MCP_STDIO example
│   ├── mcp-streamable-http.integration.json # MCP_STREAMABLE_HTTP example
│   └── http-tool.integration.json           # HTTP_TOOL example
└── <vendor>/            # one folder per integration
    ├── README.md         # the recipe (section order fixed)
    └── <vendor-id>.json  # the one integration-definition file for the chosen tier
```

- A `<vendor>/README.md` follows the section order in [`_template/README.md`](./_template/README.md);
  its "Config file" section points to the folder's JSON file and explains each placeholder — it does
  not re-inline the full JSON.
- The JSON file is what an operator copies, fills in, and drops into their `integrations.dir` (or
  translates into the equivalent programmatic call — see §3). Keep only the one file for the chosen
  tier; delete the template's other two example files.
- [`README.md`](./README.md) is the index table linking each `<vendor>/` folder.

Per-vendor facts (server id/package, capabilities, secret refs, run method) are **owner-supplied
input**, not invented. A vendor fact a recipe needs but lacks is a **content gap** — request it; do
not fabricate server ids, capabilities, or env vars.
