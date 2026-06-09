# Airtable — records over the REST API as governed capabilities

> **Tier:** `HTTP` — used here as the fallback tier: a stable REST API with no official MCP server,
> documented as a code-defined `HttpToolProvider` (`agentforge4j-tools-http`), one
> `HttpEndpointDefinition` per capability. Governed identically to MCP tools (schema, policy, audit,
> retry/timeout).

AgentForge4j makes the governed HTTP calls itself through `HttpToolProvider`; there is no server to
run. The operator supplies a secret resolver that returns the API token at invoke time.

## Capabilities

| Capability | Description | Access | Recommended policy |
|---|---|---|---|
| `airtable.list_records` | List records in a table | read-only | `ALLOW` |
| `airtable.create_record` | Create a record in a table | **mutating** | **`REQUIRE_APPROVAL`** |

## Config files

HTTP-tier recipes have no MCP server config. [`http-endpoints.json`](./http-endpoints.json) is an
array of `HttpEndpointDefinition`-shaped objects (one per capability) you translate into the
code-defined provider; the field names match the
[`HttpEndpointDefinition`](../CONVENTIONS.md) record.

- `urlTemplate` uses `{baseId}`/`{tableName}` placeholders filled from the tool arguments;
  `queryArgs` route to the query string; `bodyMode: "JSON"` builds the request body from the
  remaining arguments.
- `secretHeaders` maps a header name to a **secret-reference key** (`Authorization → AIRTABLE_TOKEN`);
  the resolver returns the full header value (e.g. `Bearer …`) at invoke. `staticHeaders` are literal,
  non-secret headers.

Wire it through the bootstrap tool-provider hook (the API token is resolved at invoke, never inline):

```java
List<HttpEndpointDefinition> endpoints = loadFrom("http-endpoints.json"); // map JSON -> records
AgentForge4j af = AgentForge4jBootstrap.defaults()
    .withToolProviders(List.of(new HttpToolProvider(
        "airtable", endpoints, secretResolver, HttpClient.newHttpClient(),
        ToolExecutionOptions.defaults(), 1_048_576L)))
    .build();
```

## Secrets

| Secret-reference key | What it is |
|---|---|
| `AIRTABLE_TOKEN` | An Airtable personal access token (with the minimum scopes for the bound bases/tables). The resolver returns the full header value (`Bearer <token>`); only the reference key appears in the JSON. |

Store the token in your vault and resolve it by reference — never inline a token in a definition or
route it through tool arguments.

## Operator run instructions

Nothing to run. Wire the secret resolver to your vault so `AIRTABLE_TOKEN` resolves to a valid
`Bearer …` header at invoke time, and register the provider as shown above.

## Governance notes

- Gate the mutating capability with `REQUIRE_APPROVAL` (policy is by capability id, identical to the
  MCP tiers):

  ```json
  { "capability": "airtable.create_record", "decision": "REQUIRE_APPROVAL", "reason": "Writes a record to a real base." }
  ```

- `airtable.list_records` may stay `ALLOW`.
- No `stdio` subprocess and no container — the provider makes outbound HTTPS calls under the runtime
  policy, schema validation, audit, and retry/timeout, the same as any tool.

## Caveats / version pins

- Confirm there is still **no official/maintained Airtable MCP server** before keeping this at the
  HTTP tier — if one appears, prefer a Tier-1 MCP recipe (see [GitHub](../github/README.md)).
- Pin to the Airtable REST API version in the `urlTemplate` (`/v0/`) and verify field/endpoint
  shapes against the current Airtable API docs; respect the per-base rate limit.
- `baseId`/`tableName` are percent-encoded into the path; ensure the bound base/table ids are
  supplied as arguments, not embedded as secrets.
