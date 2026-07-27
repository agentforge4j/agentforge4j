# agentforge4j-llm-api

The provider-neutral contract for invoking a large language model: the request and response types,
the client interface, the capability-tier enum, and the retry policy. No transport, no vendor, no
JSON.

## Why it exists

The framework talks to every LLM through one small, stable interface so that the runtime and the
agent layer never depend on a specific vendor's SDK or wire format. An agent asks for a *capability
tier* rather than a model id, and a provider module maps that tier to a concrete model. Pinning
those abstractions in their own foundation module — separate from the implementation in
`agentforge4j-llm` — keeps the contract tiny and lets the provider adapters and the runtime share it
without pulling in transport or parsing code.

## How it fits

`agentforge4j-llm-api` depends only on [`agentforge4j-util`](../agentforge4j-util/README.md). It is
required by [`agentforge4j-llm`](../agentforge4j-llm/README.md) (which implements provider discovery
against it), by the runtime, and by every provider adapter. It has no third-party runtime
dependencies.

## Key public types

| Type | Purpose |
|---|---|
| `ModelTier` | Capability tier an agent requests instead of a model id: `LITE`, `STANDARD`, `POWERFUL` (with `fromName`). |
| `LlmClient` | The provider-bound invocation contract: `getProviderName()`, `execute(LlmExecutionRequest)`, `getRetryPolicy()` (nullable). |
| `LlmExecutionRequest` | Provider-neutral request: model, system prompt, user input, token cap, prompt-layer boundaries, optional invocation identity. |
| `LlmExecutionResponse` | The result of one invocation: text, model used, token usage. |
| `TokenUsageReport` | Token counts including cache-read and cache-write accounting. |
| `LlmInvocationIdentity` | Optional origin metadata (workflow / run / step / agent ids) for observability. |
| `PromptLayerBoundaries` | Byte offsets marking prompt-cache layer boundaries. |
| `LlmRetryPolicy` | Immutable retry settings (max attempts, backoff bounds) with `defaults()`. |
| `ModelTierResolver` | Maps a `ModelTier` to a concrete model per provider. |
| `LlmInvocationException` | Failure raised by an invocation. |

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-api</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.api;
```

Exports the single package `com.agentforge4j.llm.api`.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
