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

All modules live in a single Maven monorepo. Everything is versioned and released together. **Java 17**, JPMS `module-info.java` in every module. Current version `0.0.1-SNAPSHOT`.

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
| `agentforge4j-llm-bedrock` | AWS Bedrock placeholder (SigV4 deferred) | Built |
| `agentforge4j-config-loader` | Loads agent and workflow definitions from filesystem/classpath | Built |
| `agentforge4j-schema` | JSON schemas as classpath resources | Built |
| `agentforge4j-workflows` | Shipped workflows and agents as classpath resources | Built |
| `agentforge4j-runtime` | Workflow execution state and command model | Built (currently in platform repo, relocation pending) |
| `agentforge4j-integrations` | Integration plugin model for calling external endpoints | Built (currently in platform repo, relocation pending) |
| `agentforge4j-spring-boot-starter` | Spring Boot auto-configuration library | Built (currently in platform repo, relocation pending) |
| `agentforge4j-quarkus-extension` | Quarkus auto-configuration | Planned |

---

## Dependency Direction — Strictly One Way

Never suggest a dependency that violates this chain:

```
util
  ↑
core          llm-api
  ↑             ↑
config-loader   llm ── llm-openai / llm-ollama / llm-claude / llm-vllm / llm-gemini /
  ↑             llm-mistral / llm-azure-openai / llm-openai-compatible / llm-bedrock
schema   workflows   integrations
  ↑       ↑           ↑
runtime ────────────────┘
  ↑
spring-boot-starter
```

- `core` never depends on `config-loader`
- `config-loader` never depends on `runtime`
- `llm-api` and `llm` have no workflow knowledge — they must not import anything from `core` or `config-loader`
- `util` has no dependencies beyond the JDK and `commons-lang3`
- `schema` has no dependencies beyond the JDK
- `workflows` has no dependencies beyond the JDK
- `integrations` depends on `util` only
- `runtime` depends on `core`, `config-loader`, `llm`, `integrations`, `schema`, `util`
- `spring-boot-starter` depends on `runtime`, `core`, `llm`, `config-loader`, `integrations`, `util`, and Spring Boot autoconfigure (library, not a runnable app)
- The starter never depends on `api`
- Spring, databases, and file IO never appear in `util`, `core`, `llm-api`, `llm`, `schema`, or `workflows`

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
- `Executable` — non-sealed interface, Jackson discriminator `kind`, subtypes: `STEP`, `BLUEPRINT` (ref), `WORKFLOW`
- `StepDefinition` — record: `stepId`, `name`, `StepBehaviour`, `ContextMapping`
- `StepBehaviour` — sealed interface with **8 subtypes**: `AgentBehaviour`, `SparBehaviour`, `WorkflowBehaviour`, `InputBehaviour`, `ResourceBehaviour`, `BranchBehaviour`, `FailBehaviour`, `RetryPreviousBehaviour`
- `BlueprintDefinition` — reusable named sequence of steps defined inside a workflow directory
- `BlueprintRef` — reference to a blueprint, resolved at execution time
- `LoopConfig` — record on `BlueprintRef` for iteration
- `ArtifactItem` — sealed interface: `TextArtifactItem`, `TextAreaArtifactItem`, `SingleChoiceArtifactItem`, `MultiChoiceArtifactItem`, `BooleanArtifactItem`, `NumberArtifactItem`, `DateArtifactItem`

### State concepts
- `WorkflowState` — mutable final class with `@Getter`. Final fields: `runId`, `workflowId`, nullable `parentRunId`, `startedAt`. Mutable with `@Setter`: `currentStepId`, `status`, `lastUpdatedAt`, nullable `pendingArtifact`. Mutable maps (no setters): `stepOutputs`, `context`, `stepExecutionUid`, `contextKeyWrittenAtUid`
- `WorkflowStatus` — enum: `RUNNING`, `PAUSED`, `AWAITING_INPUT`, `AWAITING_APPROVAL`, `COMPLETED`, `FAILED`, `CANCELLED`
- `WorkflowEvent` — immutable append-only audit record
- `WorkflowEventType` — enum: `RUN_STARTED`, `STEP_STARTED`, `STEP_COMPLETED`, `STEP_FAILED`, `STEP_RETRIED`, `AWAITING_INPUT`, `AWAITING_APPROVAL`, `APPROVED`, `REJECTED`, `LLM_OUTPUT`, `CONTEXT_UPDATED`, `LOOP_ITERATION_STARTED`, `LOOP_ITERATION_COMPLETED`, `RUN_COMPLETED`, `RUN_FAILED`, `RUN_CANCELLED`, `AGENT_SWAPPED`, `PROMPT_OVERRIDDEN`, `USER_PROMPT_LIMIT_REACHED`
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

### Integration concepts
- `AgentIntegration` interface: `integrationId()`, `execute(String operation, Map<String, Object> payload)` returning String
- `IntegrationConfig` — record: `enabled`, nullable `baseUrl`, nullable `apiKey`, `allowedOperations` List
- `IntegrationRegistry` interface, `DefaultIntegrationRegistry` and `NoOpIntegrationRegistry.INSTANCE`
- Credentials are **never** in workflow JSON or agent prompts — operator environment variables only.

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

Bundled (classpath) agents and workflows live in `agentforge4j-workflows` and are routed via bundle index files (`AgentBundleLocator`, `WorkflowBundleLocator`).

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

`CreateFileCommand`, `UserPromptCommand`, `SetContextCommand`, `RunCommandCommand`, `CompleteCommand`, `ContinueCommand`, `GenerateQuestionsCommand`, `EscalateCommand`, `CallEndpointCommand`, plus the legacy `COMPLETE` variants. Jackson `@JsonTypeInfo` with `type` discriminator.

---

## Structured Output — Never Parse Free Text

Agents do not return prose that the runtime interprets. They return a JSON array of typed command objects. The runtime dispatches on command type to produce real-world side effects. If you are about to write code that parses or pattern-matches free text from an LLM response, stop — use the structured command model instead.

---

## Security — Path Traversal

All file loading code uses `Validate.requireWithinBase()` to prevent path traversal. Never construct a `Path` from user-supplied or config-supplied input without validating it stays within the expected base directory. This is enforced in `FileSystemAgentPromptResolver`, `FileSystemAgentLoader`, `FileSystemWorkflowLoader`, and `LocalFileSink` and must be maintained in any new file loading code.

---

## What Is Built — Do Not Redesign

The following modules are complete and reviewed. Suggestions that change their architecture or public API are not welcome unless a bug is being fixed:

- `agentforge4j-util`, `agentforge4j-core`, `agentforge4j-llm`, `agentforge4j-schema`, `agentforge4j-workflows`, `agentforge4j-config-loader`, all 9 LLM provider modules.
- `agentforge4j-runtime`, `agentforge4j-integrations`, `agentforge4j-spring-boot-starter` are built but physically reside in the platform repo pending relocation to OSS.

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
