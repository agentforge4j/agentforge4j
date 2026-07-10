# AgentForge4j — Copilot Instructions

You are assisting with **AgentForge4j**, an open-source Java framework for orchestrating structured multi-agent AI workflows. Read this file fully before suggesting any code. These instructions apply to every file in this repository.

---

## What This Project Is

AgentForge4j is a Camunda-inspired workflow engine purpose-built for AI agent orchestration. Workflows are fully defined by external configuration files — no decisions about which agent to use are made at runtime. Every workflow run is predictable, auditable, and repeatable.

The framework is designed to be embedded by other developers across many domains: software generation, career planning, team building, competition design, and any other structured multi-agent process. It is not a software-development-only tool.

---

## Core Philosophy — Never Violate These

- **No hardcoded agents or workflows in Java code. Ever.** All agent definitions live in `agentId.agent/` directories as JSON and markdown files. All workflow definitions live in `workflowId.workflow/` directories. If you are about to write an agent name or a workflow step as a Java string literal or constant, stop.
- **Workflows must be predictable.** The workflow author decides which agent runs at each step when designing the workflow. The runtime executes that decision faithfully. The runtime never selects agents dynamically.
- **Fail fast with clear messages.** Validation happens at load time and at construction time. Errors include file paths, ids, and enough context for the developer to find the problem immediately.

---

## Module Structure

All modules live in a single Maven monorepo. Everything is versioned and released together (the workflows catalog is independently versioned). **Java 17**, JPMS `module-info.java` in every module **except** `agentforge4j-spring-boot-starter`, `agentforge4j-mcp`, and the resource-only `agentforge4j-workflow-fixtures` / `agentforge4j-workflows-catalog`. Current version `0.0.1-SNAPSHOT`.

| Module | Purpose | Status |
|---|---|---|
| `agentforge4j-util` | Shared `Validate` utility, no framework deps | Built |
| `agentforge4j-core` | Pure Java domain model, no Spring, no IO, no DB | Built |
| `agentforge4j-llm-api` | Provider-neutral LLM invocation contract (`LlmClient`, requests, errors) | Built |
| `agentforge4j-llm` | LLM SPI, resolvers, HTTP helpers; no workflow knowledge | Built |
| `agentforge4j-llm-openai` | OpenAI provider (Responses API + chat DTOs) | Built |
| `agentforge4j-llm-ollama` | Ollama provider | Built |
| `agentforge4j-llm-claude` | Anthropic Claude provider | Built |
| `agentforge4j-llm-vllm` | vLLM provider | Built |
| `agentforge4j-llm-gemini` | Google Gemini provider | Built |
| `agentforge4j-llm-mistral` | Mistral provider | Built |
| `agentforge4j-llm-azure-openai` | Azure OpenAI provider | Built |
| `agentforge4j-llm-openai-compatible` | Generic OpenAI-compatible provider | Built |
| `agentforge4j-llm-bedrock` | AWS Bedrock provider (Anthropic Claude via InvokeModel, AWS SDK + SigV4) | Built |
| `agentforge4j-llm-fake` | Deterministic scripted fake provider for tests | Built |
| `agentforge4j-config-loader` | Loads agent, workflow, and integration definitions from filesystem/classpath | Built |
| `agentforge4j-schema` | JSON schemas as classpath resources | Built |
| `agentforge4j-mcp` | MCP client integration (stdio + streamable-HTTP) contributing tool providers | Built |
| `agentforge4j-tools-http` | HTTP tool provider: governed external HTTP calls as tool-SPI `ToolProvider`s | Built |
| `agentforge4j-runtime` | Workflow execution engine: run state, events, command model | Built |
| `agentforge4j-bootstrap` | Programmatic assembly of a runnable runtime | Built |
| `agentforge4j-spring-boot-starter` | Spring Boot auto-configuration library | Built |
| `agentforge4j-testkit` | Consumer test support (fake-provider scenarios, assertions) | Built |
| `agentforge4j-workflows-catalog` | Independently versioned resources-only catalog of shipped workflows/agents (ships empty today) | Built |
| `agentforge4j-workflow-fixtures`, `agentforge4j-testkit-consumer-verification`, `agentforge4j-oss-verification`, `agentforge4j-starter-verification` | Internal fixtures and build-verification modules (never published) | Built |
| `agentforge4j-quarkus-extension` | Quarkus auto-configuration | Planned |

Outside the reactor: `agentforge4j-workflow-builder` (React component library, npm), `agentforge4j-web-ui`, `agentforge4j-ui-e2e` (Playwright), `agentforge4j-examples` (standalone Maven tree).

---

## Dependency Direction — Strictly One Way

Never suggest a dependency that violates this chain:

```
util
 ├── llm-api ── llm ── llm-openai / llm-ollama / llm-claude / llm-vllm / llm-gemini /
 │              llm-mistral / llm-azure-openai / llm-openai-compatible / llm-bedrock / llm-fake
 ├── schema
 └── core
      ├── mcp
      ├── tools-http
      └── config-loader (core + schema + util)
           └── runtime (core + config-loader + llm-api + llm + schema + util)
                └── bootstrap (runtime + tools-http + the modules above)
                     └── spring-boot-starter (bootstrap + the provider modules)
```

- `core` never depends on `config-loader`, and never on `llm-api`/`llm` — the model tier on agent/step definitions is an opaque `String`
- `config-loader` never depends on `runtime`
- `llm-api` and `llm` have no workflow knowledge — they must not import anything from `core` or `config-loader`
- `util` has no dependencies beyond the JDK and `commons-lang3`
- `schema` depends on `util` only
- The integration SPI lives in `core` (`com.agentforge4j.core.spi.integration`); `mcp` and `tools-http` contribute `IntegrationToolProviderFactory` implementations
- `runtime` depends on `core`, `config-loader`, `llm-api`, `llm`, `schema`, `util`
- `bootstrap` depends on `runtime`, `tools-http`, `config-loader`, `core`, `llm-api`, `llm`, `schema`, `util`
- `spring-boot-starter` depends on `bootstrap` (plus the provider modules) and Spring Boot autoconfigure (library, not a runnable app)
- `agentforge4j-workflows-catalog` is resources-only; no reactor module depends on it
- Spring, databases, and file IO never appear in `util`, `core`, `llm-api`, `llm`, or `schema`

If you are about to add an import that crosses these boundaries, stop and reconsider the design.

---

## Coding Standards

These are non-negotiable. Flag any existing code that violates them.

### Language and syntax

- **Java 17 throughout the OSS framework.** Use modern Java features where they improve clarity.
- **No `var` keyword.** Explicit types everywhere.
- **Braces always required** on `if`, `else`, `for`, `while`, and `do-while` blocks — even single-line bodies.
- **`formatted()` for string interpolation** in error messages, not `String.format()` and not concatenation.
- **Switch expressions with pattern matching** for `Executable` and `StepBehaviour` dispatch. Exhaustive switches include a default branch with a clear error message.
- **Classes are `final`** unless explicitly designed for extension.

### Validation

- **`Validate.*` for all validation.** Never raw `if (x == null) throw new IllegalArgumentException(...)`. The `Validate` class in `agentforge4j-util` is the only validation mechanism.
- Validation happens in compact constructors on records and in constructors on mutable classes.
- Error messages must include the offending value, the field name, and — for file loading — the file path.
- `Validate` methods provide String-message and `Supplier<RuntimeException>` overloads: `notBlank`, `notNull`, `isTrue`, `notEmpty`, `requireWithinBase`, `requireDirectory`, `isBetween`.

### Records and Lombok

- **Records for all immutable domain models and DTOs.**
- **No Lombok on records.** Records generate their own constructors and accessors.
- **Lombok `@RequiredArgsConstructor`** for constructor injection on non-record classes.
- **Lombok `@Getter` and `@Setter`** on mutable classes where needed (e.g. `WorkflowState`).
- Never use `@Data` — it generates `equals`, `hashCode`, and `toString` that may not be appropriate.

### Collections and immutability

- **`Map.copyOf()`** for immutable map snapshots.
- **`List.copyOf()`** for immutable list snapshots.
- Defensive copy mutable inputs in constructors where the record or class must own its data.

### Logging

- **`System.Logger`** in all framework-agnostic modules: `util`, `core`, `llm`, `config-loader`, `schema`, `workflows`, `integrations`.
- **Never Slf4j or Log4j** in those modules — they must remain framework-agnostic.
- Slf4j is acceptable in `runtime` and `spring-boot-starter` only.

### Naming

- Provider names are **lowercase single words** or hyphenated short forms matching the module suffix: `openai`, `ollama`, `claude`, `vllm`, `gemini`, `mistral`, `azure-openai`, `openai-compatible`, `bedrock`.
- Agent ids match their directory name stem exactly.
- Workflow ids match their directory name stem exactly.
- Blueprint ids and artifact ids match their filename stems exactly.

### Imports and modules

- **JPMS `module-info.java` in every module.** Every new class must be in a package that is either exported or internal by design.
- Internal classes (not exported) stay internal. Do not export implementation details.
- Use `StringUtils.isBlank()` from `commons-lang3` in preference to manual null-and-blank checks where `commons-lang3` is available.

### Timeouts and durations

- **`java.time.Duration`** for all timeout configuration. Never raw `int` or `long` milliseconds.

---

## Domain Model — Key Types to Know

### Agent concepts
- `AgentDefinition` — record: `id`, `name`, `AgentLocality`, `enabled`, resolved `systemPrompt` (role + boundaries merged at load time), ordered `List<ProviderPreference>`, optional `List<String> supportedCommands`
- `AgentLocality` — enum: `LOCAL` (operator-controlled, e.g. Ollama), `CLOUD` (external API, e.g. OpenAI, Claude, Gemini)
- `ProviderPreference` — record: `provider` String, nullable `model` String (null means provider default)
- `AgentRepository` interface: `get(String id)` returning `AgentDefinition`, throws `AgentNotFoundException`

### Workflow concepts
- `WorkflowDefinition` — record implementing `Executable`: `id`, `name`, nullable `description`, `Map<String, ArtifactDefinition> artifacts`, `Map<String, BlueprintDefinition> blueprints`, `List<Executable> steps`
- `Executable` — sealed interface (`permits WorkflowDefinition, StepDefinition, BlueprintRef`), Jackson discriminator `kind`, subtypes: `WORKFLOW`, `STEP`, `BLUEPRINT` (ref)
- `StepDefinition` — record: `stepId`, `name`, `StepBehaviour`, `ContextMapping`
- `StepBehaviour` — sealed interface with **11 subtypes**: `AgentBehaviour`, `SparBehaviour`, `WorkflowBehaviour`, `InputBehaviour`, `ResourceBehaviour`, `BranchBehaviour`, `FailBehaviour`, `RetryPreviousBehaviour`, `ValidateBehaviour`, `AssignContextBehaviour`, `CollectionBehaviour`
- `BlueprintDefinition` — reusable named sequence of steps defined inside a workflow directory
- `BlueprintRef` — reference to a blueprint, resolved at execution time
- `LoopConfig` — record on `BlueprintRef` for iteration
- `ArtifactItem` — sealed interface: `TextArtifactItem`, `TextAreaArtifactItem`, `SingleChoiceArtifactItem`, `MultiChoiceArtifactItem`, `BooleanArtifactItem`, `NumberArtifactItem`, `DateArtifactItem`

### State concepts
- `WorkflowState` — mutable final class with `@Getter`. Final fields: `runId`, `workflowId`, nullable `parentRunId`, `startedAt`. Mutable with `@Setter`: `currentStepId`, `status`, `lastUpdatedAt`, nullable `pendingArtifact`. Mutable maps (no setters): `stepOutputs`, `context`, `stepExecutionUid`, `contextKeyWrittenAtUid`
- `WorkflowStatus` — enum: `RUNNING`, `PAUSED`, `AWAITING_INPUT`, `AWAITING_APPROVAL`, `AWAITING_TOOL_APPROVAL`, `AWAITING_TOOL_DECISION`, `AWAITING_REVIEW`, `AWAITING_STEP_APPROVAL`, `AWAITING_COLLECTION`, `COMPLETED`, `FAILED`, `CANCELLED`
- `WorkflowEvent` — immutable append-only audit record
- `WorkflowEventType` — a large and growing enum (40+ values as of this writing — check the source,
  do not hardcode a count in a prompt or doc). Categories: run lifecycle (`RUN_STARTED`,
  `RUN_COMPLETED`, `RUN_FAILED`, `RUN_CANCELLED`, `RUN_BLOCKED`), step lifecycle (`STEP_STARTED`,
  `STEP_COMPLETED`, `STEP_FAILED`, `STEP_RETRIED`, `STEP_AWAITING_REVIEW`/`STEP_REVIEWED`,
  `STEP_AWAITING_APPROVAL`/`STEP_APPROVED`/`STEP_REJECTED`), gates (`AWAITING_INPUT`,
  `AWAITING_APPROVAL`, `APPROVED`, `REJECTED`), tool invocation (`TOOL_INVOCATION_REQUESTED`,
  `TOOL_INVOCATION_COMPLETED`, `TOOL_INVOCATION_DENIED`, `TOOL_INVOCATION_APPROVAL_PENDING`,
  `TOOL_INVOCATION_FAILED`), collection gates (`COLLECTION_OPENED`, `COLLECTION_ITEM_SUBMITTED`,
  `COLLECTION_ITEM_REPLACED`, `COLLECTION_ITEM_WITHDRAWN`, `COLLECTION_ITEM_REJECTED`,
  `COLLECTION_CLOSE_REQUESTED`, `COLLECTION_CLOSE_REJECTED`, `COLLECTION_CLOSED`,
  `COLLECTION_REOPENED`, `COLLECTION_AUTHORIZATION_DENIED`, `COLLECTION_DEADLINE_CLOSE_REQUESTED`),
  and loop/context/LLM diagnostics (`LOOP_ITERATION_STARTED`, `LOOP_ITERATION_COMPLETED`,
  `CONTEXT_UPDATED`, `LLM_OUTPUT`, `LLM_CALL_COMPLETED`, `USAGE_RECORD_FAILED`, `AGENT_SWAPPED`,
  `PROMPT_OVERRIDDEN`, `USER_PROMPT_LIMIT_REACHED`).
- `ContextValue` — sealed interface: `StringContextValue`, `NumberContextValue`, `BooleanContextValue`, `JsonContextValue`, `ContextValueList`. All records.

### Retry and loop concepts
- `RetryPolicy` — record with factory methods `none()` and `simple(int maxAttempts)`
- `LoopTerminationStrategy` — enum: `AGENT_SIGNAL`, `EVALUATOR`, `FIXED_COUNT`, `FOR_EACH`
- `MaxIterationsAction` — enum: `AWAIT_USER`, `FAIL`

### Runtime concepts
- `WorkflowRuntime` interface — lives in `core` (preserves DIP for callers): `start`, `continueRun`, `retry`, `approve`, `submitInput`, `getState`
- `DefaultWorkflowRuntime` — built via `WorkflowRuntimeBuilder` in `runtime` module
- Drive-loop model: `DefaultWorkflowRuntime` → `ExecutableExecutor` → `StepSequenceExecutor` / `BlueprintExecutor` / `WorkflowExecutor` / `StepExecutor` → `BehaviourHandler` implementations (one per `StepBehaviour` subtype)
- 4 `LoopStrategy` implementations: `FixedCountLoopStrategy`, `ForEachLoopStrategy`, `AgentSignalLoopStrategy`, `EvaluatorLoopStrategy`
- `AgentInvoker` → `ContextRenderer` → `LlmCommandParser` → `CommandApplier` pipeline (with `currentStepUid` propagation)
- `StepSequenceExecutor` skips steps already in `state.stepOutputs` (resume-safety)
- Circular construction broken with late-bound setters + "set exactly once" guards

### External HTTP calls
- Governed external HTTP calls are made through the tool SPI via `agentforge4j-tools-http`
  (`HttpToolProvider` + code-defined `HttpEndpointDefinition`s), resolved like any other
  `ToolProvider`.
- Credentials are **never** in workflow JSON or agent prompts — secret header values are resolved
  through a consumer-supplied secret resolver at invoke time, never inlined.

---

## Filesystem Layout

```
agents/
  agentId.agent/
    agent.json        # agent definition
    systemprompt.md   # role prompt
    boundaries.md     # optional, appended to system prompt at load time

workflows/
  workflowId.workflow/
    workflow.json                 # workflow definition
    blueprintId.blueprint.json    # zero or more blueprints
    artifactId.artifact.json      # zero or more artifacts
```

Agent ids, workflow ids, blueprint ids, and artifact ids must match their directory or filename stems exactly. The config-loader validates this at load time and fails fast on mismatch.

Bundled (classpath) agents and workflows live in `agentforge4j-workflows-catalog` and are routed via bundle index files (`AgentBundleLocator`, `WorkflowBundleLocator`).

---

## LLM Provider Model

- `LlmClient` — interface: `execute(LlmExecutionRequest)` returning `String`
- `LlmClientFactory` — registered via JPMS `ServiceLoader`, one factory per provider module
- `AbstractHttpLlmClient` — template method base class for HTTP-based providers
- `RetryingLlmClient` / `RetryingLlmClientResolver` — retry wrapper
- `DefaultLlmClientResolver` — resolves provider name to client
- `LlmExecutionRequest` — record: `provider`, nullable `model`, `systemPrompt`, `userInput`
- Agents return **structured JSON arrays of typed commands** — never free-form prose for the runtime to parse

### LlmCommand subtypes (sealed)

`CreateFileCommand`, `UserPromptCommand`, `SetContextCommand`, `RunCommandCommand`, `CompleteCommand`, `ContinueCommand`, `GenerateQuestionsCommand`, `EscalateCommand`, `ToolInvocationCommand`. Jackson `@JsonTypeInfo` with `type` discriminator.

---

## Structured Output — Never Parse Free Text

Agents do not return prose that the runtime interprets. They return a JSON array of typed command objects. The runtime dispatches on command type to produce real-world side effects. If you are about to write code that parses or pattern-matches free text from an LLM response, stop — use the structured command model instead.

---

## Security — Path Traversal

All file loading code uses `Validate.requireWithinBase()` to prevent path traversal. Never construct a `Path` from user-supplied or config-supplied input without validating it stays within the expected base directory. This is enforced in `FileSystemAgentPromptResolver`, `FileSystemAgentLoader`, `FileSystemWorkflowLoader`, and `LocalFileSink` and must be maintained in any new file loading code.

---

## What Is Built — Do Not Redesign

The following modules are complete and reviewed. Suggestions that change their architecture or public API are not welcome unless a bug is being fixed:

- `agentforge4j-util`, `agentforge4j-core`, `agentforge4j-llm-api`, `agentforge4j-llm`, `agentforge4j-schema`, `agentforge4j-config-loader`, `agentforge4j-mcp`, `agentforge4j-tools-http`, `agentforge4j-runtime`, `agentforge4j-bootstrap`, `agentforge4j-spring-boot-starter`, `agentforge4j-testkit`, and all 10 LLM provider modules.

---

## Specialised Agent Prompts

For focused tasks, use the agent prompts in `.github/agents/`:

- **`@javadoc-agent`** — adds missing Javadoc to public API surface following project conventions
- **`@unit-test-agent`** — generates JUnit 5 unit tests with module-specific coverage guidance
- **`@review-agent`** — performs code review with module-specific guidance, leaves findings as a report; does not auto-fix
- **`@commit-agent`** — stages files and writes commit messages following Conventional Commits format with explicit developer confirmation
- **`@changelog-agent`** — generates a pending changelog file from the current Git diff
- **`@module-quality-orchestrator`** — orchestrates the full quality pass (javadoc → tests → build → review → changelog) over a single module's diff

These agents inherit the context from this file. Reference them for their specific task rather than repeating this context in your prompt.
