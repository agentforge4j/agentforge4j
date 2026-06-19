# agentforge4j-llm-fake

A deterministic, scripted LLM provider for tests, demos, and workflow verification.

> ⚠️ **Not for production.** This provider returns pre-scripted responses from an in-memory store and
> never contacts a model. It exists for local development, demos, sandbox mode, and automated
> workflow verification. Do not deploy it as a real provider.

## Why it exists

Testing a workflow against a live LLM is slow, costly, and non-deterministic. This provider replaces
the model with a scripted source: you register the exact responses a run should receive, keyed by
invocation identity, and the workflow executes deterministically. It is the foundation for
fast, repeatable workflow tests and for offline demos.

## How it fits

`agentforge4j-llm-fake` implements [`agentforge4j-llm`](../agentforge4j-llm/README.md)'s
`LlmClientFactory` and is discovered like any other provider via `ServiceLoader`. Because its
load-bearing input — the scripted response source — is a runtime object rather than a string, it
**cannot** be configured from environment variables or properties; it must be wired programmatically
through the bootstrap builder's `withLlmProvider(...)` escape hatch.

## Key public types

| Type | Purpose |
|---|---|
| `FakeLlmClientFactory` | The `LlmClientFactory` (provider id `"fake"`, `requiresApiKey()` is `false`); requires a `FakeConfiguration`. |
| `FakeConfiguration` | Carries the `FakeResponseSource`, a default model (`"fake-model"`), and connect timeout. |
| `FakeResponseSource` | Supplies scripted responses, advancing a per-sequence ordinal keyed by run/workflow/step/agent. |
| `RegistryFakeResponseSource` | Resolves against scripts registered per run (test-runner / demo flow). |
| `StaticFakeResponseSource` | Serves every run the same run-agnostic script (single-workflow dev/demos). |
| `FakeRunLifecycle` | Register / deregister / sweep of per-run scripts, with TTL eviction and an optional dev-only max-run cap. |

Resolution is fail-closed: a missing script or key raises `FakeResponseNotFoundException` — the fake
never fabricates a default.

## Maven coordinates

For test suites, depend on it with `test` scope:

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-fake</artifactId>
  <scope>test</scope>
</dependency>
```

For a demo, sandbox, or offline application that runs the scripted provider as part of the running
program, depend on it with the normal (compile/runtime) scope — drop `<scope>test</scope>` — since
test scope is not on the runtime classpath of the application.

## JPMS module name

```java
requires agentforge4j.llm.fake;
```

Exports `com.agentforge4j.llm.fake` and declares `provides LlmClientFactory with
FakeLlmClientFactory`.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
