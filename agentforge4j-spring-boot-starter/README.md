# agentforge4j-spring-boot-starter

Spring Boot auto-configuration for AgentForge4j. Add it to a Spring Boot application, set a few
properties, and get a ready-to-inject `AgentForge4j` bean — no manual wiring.

## Why it exists

The [`agentforge4j-bootstrap`](../agentforge4j-bootstrap/README.md) facade assembles a runtime in
plain Java. In a Spring Boot application you would rather express that assembly as configuration
properties and let the container own the lifecycle. This starter is the thin adapter that does it: it
binds `agentforge4j.*` properties, activates whichever provider adapters are on the classpath, and
delegates the actual assembly to the bootstrap facade, exposing a single `AgentForge4j` bean.

## How it fits

The starter depends on [`agentforge4j-bootstrap`](../agentforge4j-bootstrap/README.md) (and the
modules beneath it) plus Spring Boot's auto-configuration, and declares every provider adapter as an
**optional** dependency so applications add only the ones they need. It is the topmost OSS module.

## Configuration

Provider adapters are activated when their module is on the classpath and (for key-based providers)
an API key is set. Bind the following under `application.yml` / `application.properties`:

| Prefix | Bound by | Notable keys |
|---|---|---|
| `agentforge4j` | `AgentForge4jProperties` | `agents-path`, `workflows-path`, `integrations.dir`, `max-nesting-depth`, `load-shipped-agents`, `load-shipped-workflows` |
| `agentforge4j.llm.cache` | `LlmCacheSettings` | `enabled` |
| `agentforge4j.llm.model-tiers` | `ModelTierProperties` | `<provider>.<tier>` overrides of the shipped tier defaults |
| `agentforge4j.llm.<provider>` | per-provider `*LlmClientProperties` | `api-key`, `default-model`, the endpoint key (`url` for openai/claude/ollama/vllm, `base-url` for gemini/mistral/openai-compatible, `endpoint` for azure-openai; bedrock uses `region`), `connect-timeout`, `request-timeout` (provider-specific extras documented in each provider README) |
| `agentforge4j.mcp` | `McpProperties` | `servers[].{id, provider-id, transport, command, args, url, env, headers, enabled, request-timeout}` |
| `agentforge4j.tools` | `ToolProperties` | `timeout`, `max-retries`, `retry-backoff` |

```yaml
agentforge4j:
  llm:
    openai:
      api-key: ${OPENAI_API_KEY}
  load-shipped-workflows: true
```

The configuration metadata is generated, so IDEs offer completion and documentation for these keys.

### Auto-configuration classes

`BootstrapAutoConfiguration` builds the `AgentForge4j` bean; `SpringRuntimeAutoConfiguration` and
`InMemoryRuntimePersistenceAutoConfiguration` supply the runtime and default in-memory persistence;
`McpAutoConfiguration` wires configured MCP servers. Each of the ten providers has a
`*ProviderAutoConfiguration` that, once the provider module is on the classpath
(`@ConditionalOnClass`), contributes that provider's `LlmClientConfiguration` when its property gate
is met:

- **api-key set** (`agentforge4j.llm.<provider>.api-key`) — `openai`, `claude`, `gemini`, `mistral`, `azure-openai`, `openai-compatible`;
- **`enabled=true`** (`agentforge4j.llm.<provider>.enabled`) — `bedrock`, `ollama`, and the `fake` provider (`agentforge4j.llm.fake.enabled`);
- **`url` set** (`agentforge4j.llm.vllm.url`) — `vllm`.

The matching `LlmClientFactory` is discovered separately via `ServiceLoader` and bound to that
configuration during bootstrap assembly. (The `fake` provider additionally defaults a scripted
response source you can override with your own bean.)

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-spring-boot-starter</artifactId>
</dependency>
```

Add the provider modules you want (for example `agentforge4j-llm-openai`) alongside it.

## JPMS

This module has **no** `module-info.java`. The Spring programming model relies on open-package
reflection across the application, so the starter ships as a classpath (automatic) module — the
documented carve-out for the Spring integration layer.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
