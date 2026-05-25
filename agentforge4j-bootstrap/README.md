# agentforge4j-bootstrap

The `agentforge4j-bootstrap` module is the framework-agnostic entry point for assembling a fully wired [AgentForge4j](src/main/java/com/agentforge4j/bootstrap/AgentForge4j.java) runtime. It solves the problem of wiring repositories, LLM clients, file sinks, and workflow loaders without Spring or any other container. Use it in plain Java applications, unit and integration tests, CLI tools, Quarkus extensions, or anywhere you are not using the Spring Boot starter.

---

## Quick start

```java
import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;

AgentForge4j af = AgentForge4jBootstrap.defaults().build();
String runId = af.start("my-workflow");
```

`build()` loads shipped agents and workflows from the classpath by default (`withLoadShippedAgents(true)` and `withLoadShippedWorkflows(true)` are the defaults). Disable them if you supply your own definitions only.

Unless you configure a file sink, bootstrap logs a **WARNING** and uses `FileSink.NO_OP_FILE_SINK` — `CreateFileCommand` output is discarded. Suppress the warning and enable file output with either:

```java
.withFileSink(new LocalFileSink(Path.of("/output")))
// or
.withFileSinkPath(Path.of("/output"))
```

---

## Configuration via environment variables and system properties

Bootstrap reads `AGENTFORGE4J_*` environment variables and `agentforge4j.*` system properties (normalised to dot-form; system properties win on collision). LLM keys also map to `agentforge4j.llm.<provider>.*` (for example `agentforge4j.llm.openai.api-key`).

| Key | Type | Description |
|---|---|---|
| `AGENTFORGE4J_LLM_<PROVIDER>_API_KEY` | String | API key for the named provider |
| `AGENTFORGE4J_LLM_<PROVIDER>_BASE_URL` | String | Override base URL |
| `AGENTFORGE4J_LLM_<PROVIDER>_DEFAULT_MODEL` | String | Override default model |
| `AGENTFORGE4J_LLM_<PROVIDER>_CONNECT_TIMEOUT_SECONDS` | Integer | Override connect timeout |
| `agentforge4j.agents.path` | Path | Filesystem directory of agent definitions |
| `agentforge4j.workflows.path` | Path | Filesystem directory of workflow definitions |
| `agentforge4j.filesink.path` | Path | Base directory for `CreateFileCommand` output |
| `agentforge4j.llm.cache.enabled` | boolean | Enable LLM prompt caching |
| `agentforge4j.max-nesting-depth` | Integer | Max workflow nesting depth |
| `agentforge4j.load-shipped-agents` | boolean | Load bundled agents (default: `true`) |
| `agentforge4j.load-shipped-workflows` | boolean | Load bundled workflows (default: `true`) |

Programmatic `with*` calls on `AgentForge4jBootstrap.Builder` always win over environment and system-property values.

---

## LLM provider configuration

Nine static factories on [`LlmProviderConfig`](src/main/java/com/agentforge4j/bootstrap/LlmProviderConfig.java) each return a `ProviderBuilder`: `defaults()`, `apiKey(...)`, `baseUrl(...)`, `defaultModel(...)`, `connectTimeout(...)`, and `build()`.

Providers are discovered at runtime via `ServiceLoader<LlmClientFactory>`. Add the provider module JARs you need to the classpath; bootstrap wires every factory it finds.

### API-key providers

Require an API key (or env/sys-prop equivalent) unless configured programmatically:

- `openai()`
- `claude()`
- `gemini()`
- `mistral()`

### Deployment-specific

`baseUrl` and `defaultModel` are `null` by default — set both for your deployment:

- `azureOpenAi()`
- `openAiCompatible()`
- `bedrock()`

### Local / no API key

When the provider module is on the classpath, bootstrap includes these without an API key using built-in defaults:

- `ollama()`
- `vllm()`

### Example

```java
import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;

AgentForge4j af = AgentForge4jBootstrap.defaults()
    .withLlmProvider(LlmProviderConfig.openai()
        .defaults()
        .apiKey("sk-...")
        .build())
    .build();
```

`withLlmProvider` is **additive** across provider keys; calling it again for the same provider replaces that provider’s config (last-write-wins within a key).

### Opt-in LLM retry

`RetryingLlmClientResolver` is not applied automatically. Pass a wrapped resolver via `withLlmClientResolver` before `build()`:

```java
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmRetryPolicy;

// After a first build, or with any LlmClientResolver delegate:
LlmClientResolver resolver = new RetryingLlmClientResolver(
    agentForge4j.components().llmClientResolver(),
    new LlmRetryPolicy(3, 200, 10_000, 30_000));

AgentForge4j af = AgentForge4jBootstrap.defaults()
    .withLlmClientResolver(resolver)
    .build();
```

`LlmRetryPolicy.defaults()` is equivalent to `new LlmRetryPolicy(3, 200, 10_000, 30_000)`.

---

## Overriding defaults

`AgentForge4jBootstrap.defaults()` returns a builder pre-populated with in-memory repositories, `Clock.systemUTC()`, a Jackson `ObjectMapper` with `JavaTimeModule`, and other stock components. Override only what you need:

```java
import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.runtime.command.LocalFileSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

AgentForge4j af = AgentForge4jBootstrap.defaults()
    .withFileSink(new LocalFileSink(Path.of("/output")))
    .withObjectMapper(new ObjectMapper())
    .withAgentsDir(Path.of("/my-agents"))
    .withLoadShippedAgents(false)
    .withLoadShippedWorkflows(false)
    .withClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
    .build();
```

| Override | Method |
|---|---|
| Clock | `withClock(Clock)` |
| Jackson mapper | `withObjectMapper(ObjectMapper)` |
| Agent repository | `withAgentRepository(AgentRepository)` |
| Workflow repository | `withWorkflowRepository(WorkflowRepository)` |
| Workflow state | `withWorkflowStateRepository(WorkflowStateRepository)` |
| Event log | `withWorkflowEventLog(WorkflowEventLog)` |
| Integrations | `withIntegrationRegistry(IntegrationRegistry)` |
| LLM resolver | `withLlmClientResolver(LlmClientResolver)` |
| Context renderer | `withContextRenderer(ContextRenderer)` |
| LLM command parser | `withLlmCommandParser(LlmCommandParser)` |
| Event recorder | `withEventRecorder(EventRecorder)` |
| File sink instance | `withFileSink(FileSink)` |
| File sink directory | `withFileSinkPath(Path)` |
| Provider selection | `withLlmProviderSelectionStrategy(LlmProviderSelectionStrategy)` |
| Agent invoker | `withAgentInvoker(AgentInvoker)` |
| LLM call observer | `withLlmCallObserver(LlmCallObserver)` |
| Max nesting depth | `withMaxNestingDepth(int)` |
| Agents directory | `withAgentsDir(Path)` |
| Workflows directory | `withWorkflowsDir(Path)` |
| Shipped agents | `withLoadShippedAgents(boolean)` |
| Shipped workflows | `withLoadShippedWorkflows(boolean)` |
| Prompt cache | `withCacheEnabled(boolean)` |
| LLM provider | `withLlmProvider(LlmProviderConfig)` |

All `with*(null)` calls throw `IllegalArgumentException` immediately.

---

## Accessing assembled components

```java
import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.BootstrapComponents;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.llm.LlmClientResolver;
import java.util.List;

AgentForge4j af = AgentForge4jBootstrap.defaults().build();

// Runtime operations
WorkflowRuntime runtime = af.runtime();
String runId = af.start("my-workflow");
List<WorkflowDefinition> workflows = af.workflows();
List<AgentDefinition> agents = af.agents();

// Internal components (for framework integrators)
BootstrapComponents components = af.components();
AgentRepository agentRepo = components.agentRepository();
LlmClientResolver resolver = components.llmClientResolver();
```

`BootstrapComponents` is intended for framework integrators (Spring starter, Quarkus extension, CLI). Application code should use `AgentForge4j` and `WorkflowRuntime` directly.

---

## Spring Boot users

If you run on Spring Boot, use [`agentforge4j-spring-boot-starter`](../agentforge4j-spring-boot-starter/) instead of calling bootstrap yourself. The starter’s `BootstrapAutoConfiguration` delegates assembly to `AgentForge4jBootstrap` and exposes a single `AgentForge4j` bean. Inject `AgentForge4j` (or `agentForge4j.runtime()` via your own bean method) for workflow operations; use `agentForge4j.components()` only when integrating additional framework wiring.

---

## Custom `FileSink` limitation (Spring)

When using the Spring Boot starter, registering a standalone `@Bean FileSink` does **not** replace the sink used at runtime. Bootstrap assembles the `AgentForge4j` facade (including `FileSink`) before Spring can inject a custom bean. Provide a custom sink by overriding the `AgentForge4j` bean and calling `AgentForge4jBootstrap.Builder.withFileSink(...)` (or `withFileSinkPath(...)`) on the builder.

---

## Maven dependency

```xml
<dependency>
    <groupId>com.agentforge4j</groupId>
    <artifactId>agentforge4j-bootstrap</artifactId>
    <version>${agentforge4j.version}</version>
</dependency>
```

Add LLM provider modules (for example `agentforge4j-llm-openai`, `agentforge4j-llm-ollama`) to the classpath for the providers you want `ServiceLoader` to discover.
