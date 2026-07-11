# Integration recipes

Vendor-neutral, MCP-first **recipes** that turn an external service into a **governed AgentForge4j
capability**. Each recipe is documentation — a markdown page, one integration-definition JSON file,
a secret-reference list, and operator run instructions. AgentForge4j connects to and governs
servers; **it never builds, pulls, or runs them**.

- Start here: [`CONVENTIONS.md`](./CONVENTIONS.md) — tier decision rule, capability naming, secret
  and no-orchestration rules, and the grounded config shapes.
- Author a recipe: copy the [`_template/`](./_template/) folder to `docs/integrations/<vendor>/` and
  fill in its `README.md` + the one JSON file an operator copies.

## Tiers

| Tier | When | What you write |
|---|---|---|
| **MCP** (default) | an official/maintained MCP server exists | an `IntegrationDefinition` of type `MCP_STDIO` or `MCP_STREAMABLE_HTTP` |
| **HTTP** (fallback) | no MCP server, but a stable REST API | an `IntegrationDefinition` of type `HTTP_TOOL` (`agentforge4j-tools-http`) |
| **NATIVE** (exception) | rich/stateful + typed + non-trivial auth (e.g. FHIR) | a Java module — **separate code design, not these docs** |

## Recipes

Each vendor is a folder containing the recipe `README.md` plus the one integration-definition JSON
file an operator copies and fills in.

| Vendor | Tier | Transport | Capabilities (illustrative — verify per vendor, see §2) | Status |
|---|---|---|---|---|
| [GitHub](./github/README.md) | MCP | `MCP_STDIO` | `search_repositories`, `get_pull_request`, `create_pull_request`, `create_issue` | Exemplar |
| [Jira](./jira/README.md) | MCP | `MCP_STREAMABLE_HTTP` | `search_issues`, `create_issue`, `add_comment` | Exemplar |
| [Airtable](./airtable/README.md) | HTTP | — | `airtable.list_records`, `airtable.create_record` | Exemplar |

> The three rows above are **exemplars** that validate the template against the three tiers' real
> config shapes. The curated production set is owner-supplied (see
> [`CONVENTIONS.md` §4](./CONVENTIONS.md)); each new vendor is rendered into the template and
> convention-checked, never invented. MCP capability ids have no `<domain>.` prefix here because
> the runtime uses the vendor server's advertised tool name as-is (§2) — confirm the exact names
> against each server's live `tools/list` before relying on them.
