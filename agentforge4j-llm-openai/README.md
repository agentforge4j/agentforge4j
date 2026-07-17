# agentforge4j-llm-openai

The OpenAI provider for AgentForge4j — an HTTP adapter that implements `LlmClientFactory` for
OpenAI-hosted models and is discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against OpenAI's hosted models. It maps OpenAI's JSON to the
shared `agentforge4j.llm` model and keeps transport, auth headers, and DTO parsing out of the
portable core modules. It uses the JDK `java.net.http` client — no vendor SDK.

## Supported models

Shipped tier defaults for provider id `openai`:

| Tier | Default model |
|---|---|
| `LITE` | `gpt-5.4-nano` |
| `STANDARD` | `gpt-5.4-mini` |
| `POWERFUL` | `gpt-5.5` |

Agents request a tier; operators may override any tier-to-model mapping.

## How it activates

Put `agentforge4j-llm-openai` on the path. The module declares `provides LlmClientFactory with
OpenAiLlmClientFactory` (provider id `"openai"`, API key required) and exports no packages — you
select and configure it by id, never by importing its types.

## Configuration

Requires an API key and a base URL.

**Spring Boot starter** — bind under `agentforge4j.llm.openai`:

```yaml
agentforge4j:
  llm:
    openai:
      api-key: ${OPENAI_API_KEY}
      default-model: gpt-5.4-mini   # optional
      url: https://api.openai.com/v1   # required
      connect-timeout: 10s          # optional; defaults to 10s
      request-timeout: 2m           # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.openai()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md) for the env-var / system-property convention
and the programmatic factory); the builder pre-populates the OpenAI production host as its default
base URL. Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-openai</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.openai;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
