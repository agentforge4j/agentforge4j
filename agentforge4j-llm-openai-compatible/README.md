# agentforge4j-llm-openai-compatible

A generic provider for any OpenAI-compatible endpoint — gateways, proxies, and alternate hosts that
speak the OpenAI Responses contract. Implements `LlmClientFactory` and is discovered automatically
via `ServiceLoader`.

## When to use it

Choose this provider to reach an OpenAI-compatible API that is *not* one of the first-class providers
— a corporate gateway, an aggregation proxy, or a self-hosted service. Because such endpoints vary in
how they authenticate and where their Responses path lives, this adapter takes the **auth header
name**, **auth header prefix**, and **responses path** explicitly instead of assuming OpenAI's
defaults. It uses the JDK `java.net.http` client.

## Supported models

Shipped tier defaults for provider id `openai-compatible`:

| Tier | Default model |
|---|---|
| `LITE` | `gpt-5.4-nano` |
| `STANDARD` | `gpt-5.4-mini` |
| `POWERFUL` | `gpt-5.5` |

These are nominal defaults; set `default-model` and the tier mapping to whatever your endpoint
serves.

## How it activates

Put `agentforge4j-llm-openai-compatible` on the path. The module declares `provides LlmClientFactory
with OpenAiCompatibleLlmClientFactory` (provider id `"openai-compatible"`, API key required).

## Configuration

Requires the API key, the base URL, the auth header name and prefix, and the responses path. The base
URL is scheme + host (+ optional port) with no trailing slash; the responses path is appended to it.

**Spring Boot starter** — bind under `agentforge4j.llm.openai-compatible`:

```yaml
agentforge4j:
  llm:
    openai-compatible:
      api-key: ${GATEWAY_API_KEY}
      base-url: https://gateway.example.com
      auth-header-name: Authorization
      auth-header-prefix: "Bearer "      # may be an empty string for raw-key endpoints
      responses-path: /v1/responses     # this adapter speaks the OpenAI Responses API
      default-model: gpt-5.4-mini        # optional
      connect-timeout: 10s               # optional; defaults to 10s
      request-timeout: 2m                # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.openAiCompatible()`
(see the [bootstrap README](../agentforge4j-bootstrap/README.md)). Because the provider id contains a
hyphen, prefer the starter properties or the programmatic builder over environment-variable
configuration. Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-openai-compatible</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.openaicompatible;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
