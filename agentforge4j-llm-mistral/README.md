# agentforge4j-llm-mistral

The Mistral AI provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory` for
Mistral's API, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against Mistral's hosted models. It maps Mistral's REST API to
the shared `agentforge4j.llm` model and uses the JDK `java.net.http` client — no vendor SDK.

## Supported models

Shipped tier defaults for provider id `mistral`:

| Tier | Default model |
|---|---|
| `LITE` | `mistral-small-2603` |
| `STANDARD` | `mistral-medium-3-5` |
| `POWERFUL` | `mistral-large-2512` |

## How it activates

Put `agentforge4j-llm-mistral` on the path. The module declares `provides LlmClientFactory with
MistralLlmClientFactory` (provider id `"mistral"`, API key required).

## Configuration

Requires an API key. The base URL defaults to the Mistral public API host and may be overridden.

**Spring Boot starter** — bind under `agentforge4j.llm.mistral`:

```yaml
agentforge4j:
  llm:
    mistral:
      api-key: ${MISTRAL_API_KEY}
      default-model: mistral-medium-3-5   # optional
      base-url:                           # optional; overrides the default host
      connect-timeout: 10s                # optional; defaults to 10s
      request-timeout: 2m                 # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.mistral()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-mistral</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.mistral;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
