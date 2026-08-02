# Airtable — records over the REST API as governed capabilities

> **Tier:** `HTTP_TOOL` — used here as the fallback tier: a stable REST API with no official MCP
> server, documented as an `IntegrationDefinition` of type `HTTP_TOOL`
> (`agentforge4j-tools-http`), one `HttpEndpointDefinition` per capability. Governed identically to
> MCP tools (schema, policy, audit, retry/timeout).

AgentForge4j makes the governed HTTP calls itself through `HttpToolProvider`; there is no server to
run. The operator supplies a secret resolver that returns the API token at invoke time.

## Capabilities

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `airtable.list_records` | List records in a table | read-only | `Allow` |
| `airtable.create_record` | Create a record in a table | **mutating** | **`RequireApproval`** |

## Config file

Copy [`airtable.json`](./airtable.json), fill in any placeholders, and drop it into the directory
passed to `AgentForge4jBootstrap.withIntegrationsDir(...)`. The `config` array is exactly
`HttpEndpointDefinition`-shaped, one object per capability — see
[`CONVENTIONS.md`](../CONVENTIONS.md) §3 for the full field list.

- `urlTemplate` uses `{baseId}`/`{tableName}` placeholders filled from the tool arguments;
  `queryArgs` route to the query string; `bodyMode: "JSON"` builds the request body from the
  remaining arguments.
- `secretHeaders` maps a header name to a **bare secret-reference key**
  (`Authorization → AIRTABLE_TOKEN`); the resolver returns the full header value (e.g. `Bearer …`)
  at invoke. `staticHeaders` are literal, non-secret headers.

Either drop the file into `integrations.dir` as above, or wire it programmatically if you need more
control over the `SecretResolver`/`HttpClient` (the API token is resolved at invoke, never inline):

```java
List<HttpEndpointDefinition> endpoints = loadFrom("airtable.json"); // config array -> records
AgentForge4j af = AgentForge4jBootstrap.defaults()
    // HTTP tools are remote, so the secure-default policy denies them unless you opt in with
    // ToolPolicy.allowAll() or a custom capability-aware policy (see Governance notes):
    .withToolPolicy(myCapabilityAwarePolicy)
    .withToolProviders(List.of(new HttpToolProvider(
        "airtable", endpoints, secretResolver,
        // Never follow redirects: a 30x to a private/metadata host would bypass the egress guard,
        // which only validates the originally mapped URL. The factory's default client already does
        // this; when you construct your own client you must set it yourself.
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        new HttpEgressGuard(false), ToolExecutionOptions.defaults(), 1_048_576L,
        new ObjectMapper())))
    .build();
```

The `HttpEgressGuard(false)` argument refuses requests to private, loopback, link-local, and
cloud-metadata addresses (SSRF protection); pass `true` only in development. When you bypass the
factory and build your own `HttpClient`, it **must not** follow redirects (`Redirect.NEVER`) —
otherwise a redirect to a private or cloud-metadata host defeats the egress guard, which validates
only the originally mapped URL.

## Secrets

| Secret-reference key | What it is |
|---|---|
| `AIRTABLE_TOKEN` | An Airtable personal access token (with the minimum scopes for the used bases/tables). The resolver returns the full header value (`Bearer <token>`); only the reference key appears in the JSON. |

Store the token in your vault and resolve it by reference — never inline a token in the definition
or route it through tool arguments.

## Operator run instructions

Nothing to run. Wire your `SecretResolver` (or vault-backed equivalent) so `AIRTABLE_TOKEN`
resolves to a valid `Bearer …` header at invoke time.

## Governance notes

- Gate `airtable.create_record` by returning `PolicyDecision.RequireApproval(reason,
  approverScope)` for it from your own `ToolPolicy` (`AgentForge4jBootstrap.withToolPolicy(...)`),
  e.g. reason `"Writes a record to a real base."`. `airtable.list_records` may return `Allow()`.
  OSS ships no default per-capability rule table — the shipped `SecureDefaultToolPolicy` denies
  every `REMOTE_HTTP` tool (including this one) until you opt in; see
  [`CONVENTIONS.md`](../CONVENTIONS.md) §3.
- No stdio subprocess and no container — the provider makes outbound HTTPS calls under the runtime
  policy, schema validation, audit, and retry/timeout, the same as any tool.

## Caveats / version pins

- Confirm there is still **no official/maintained Airtable MCP server** before keeping this at the
  HTTP tier — if one appears, prefer an MCP recipe (see [GitHub](../github/README.md)).
- Pin to the Airtable REST API version in the `urlTemplate` (`/v0/`) and verify field/endpoint
  shapes against the current Airtable API docs; respect the per-base rate limit.
- `baseId`/`tableName` are percent-encoded into the path; ensure the used base/table ids are
  supplied as arguments, not embedded as secrets.
