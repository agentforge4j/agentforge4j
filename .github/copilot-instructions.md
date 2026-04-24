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

All modules live in a single Maven monorepo. Everything is versioned and released together. Java 26, JPMS `module-info.java` in every module.

| Module | Purpose | Status |
|---|---|---|
| `agentforge4j-util` | Shared `Validate` utility, no framework deps | Planned |
| `agentforge4j-core` | Pure Java domain model, no Spring, no IO, no DB | Planned |
| `agentforge4j-llm` | Shared LLM abstractions, no workflow knowledge | Planned |
| `agentforge4j-llm-openai` | OpenAI provider via Responses API | Planned |
| `agentforge4j-llm-ollama` | Ollama provider | Planned |
| `agentforge4j-llm-claude` | Anthropic Claude provider | Planned |
| `agentforge4j-llm-vllm` | vLLM provider | Planned |
| `agentforge4j-config-loader` | Loads agent and workflow definitions from filesystem | Planned |
| `agentforge4j-runtime` | Workflow execution state and command model | Planned |
| `agentforge4j-persistence-jdbc` | Optional JDBC persistence | Planned later |
| `agentforge4j-persistence-jpa` | Optional JPA persistence | Planned later |
| `agentforge4j-api` | Spring Boot thin facade over runtime | Planned later |
| `agentforge4j-spring-boot-starter` | Auto-configuration for Spring Boot | Planned later |
| `agentforge4j-spring-boot-starter-openai` | Auto-configures OpenAI provider | Planned later |
| `agentforge4j-spring-boot-starter-ollama` | Auto-configures Ollama provider | Planned later |
| `agentforge4j-cli` | Thin CLI facade over runtime | Planned later |

---

## Dependency Direction — Strictly One Way

Never suggest a dependency that violates this chain:

```
util
  ↑
core          llm
  ↑             ↑
config-loader   llm-openai / llm-ollama / llm-claude / llm-vllm
  ↑
runtime
  ↑
api
  ↑
starters
```

- `core` never depends on `config-loader`
- `config-loader` never depends on `runtime`
- `llm` has no workflow knowledge — it must not import anything from `core` or `config-loader`
- `util` has no dependencies beyond the JDK and `commons-lang3`
- Spring, databases, and file IO never appear in `util`, `core`, or `llm`

If you are about to add an import that crosses these boundaries, stop and reconsider the design.

---

## Coding Standards

These are non-negotiable. Flag any existing code that violates them.

### Language and syntax

- **Java 26 throughout.** Use modern Java features where they improve clarity.
- **No `var` keyword.** Explicit types everywhere.
- **Braces always required** on `if`, `else`, `for`, `while`, and `do-while` blocks — even single-line bodies.
- **`formatted()` for string interpolation** in error messages, not `String.format()` and not concatenation.
- **Switch expressions with pattern matching** for `Executable` and `StepBehaviour` dispatch.

### Validation

- **`Validate.*` for all validation.** Never raw `if (x == null) throw new IllegalArgumentException(...)`. The `Validate` class in `agentforge4j-util` is the only validation mechanism.
- Validation happens in compact constructors on records and in constructors on mutable classes.
- Error messages must include the offending value, the field name, and — for file loading — the file path.

### Records and Lombok

- **Records for all immutable domain models and DTOs.**
- **No Lombok on records.** Records generate their own constructors and accessors.
- **Lombok `@RequiredArgsConstructor`** for constructor injection on non-record classes.
- **Lombok `@Getter` and `@Setter`** on mutable classes where needed.
- Never use `@Data` — it generates `equals`, `hashCode`, and `toString` that may not be appropriate.

### Collections and immutability

- **`Map.copyOf()`** for immutable map snapshots.
- **`List.copyOf()`** for immutable list snapshots.
- Defensive copy mutable inputs in constructors where the record or class must own its data.

### Logging

- **`System.Logger`** in all framework-agnostic modules: `util`, `core`, `llm`, `config-loader`.
- **Never Slf4j or Log4j** in those modules — they must remain framework-agnostic.
- Slf4j is acceptable in `runtime`, `api`, and starters only.

### Naming

- Provider names are **lowercase single words**: `openai`, `ollama`, `claude`, `vllm`. No hyphens, no camelCase.
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
- `AgentDefinition` — record: id, name, `AgentLocality` (LOCAL or CLOUD), enabled, resolved systemPrompt, ordered list of `ProviderPreference`
- `AgentLocality` — enum: `LOCAL` (operator-controlled, e.g. Ollama), `CLOUD` (external API, e.g. OpenAI)
- `ProviderPreference` — record: provider string, nullable model string (null means provider default)

### Workflow concepts
- `WorkflowDefinition` — record implementing `Executable`: id, name, description, artifacts map, blueprints map, steps list
- `Executable` — non-sealed interface, Jackson discriminator `kind`, subtypes: `STEP`, `BLUEPRINT`, `WORKFLOW`
- `StepDefinition` — record: stepId, name, `StepBehaviour`, `ContextMapping`
- `StepBehaviour` — sealed interface, subtypes: `AgentBehaviour`, `SparBehaviour`, `WorkflowBehaviour`, `InputBehaviour`
- `BlueprintDefinition` — reusable named sequence of steps defined inside a workflow directory
- `BlueprintRef` — reference to a blueprint, resolved at execution time

### State concepts
- `WorkflowState` — mutable, runtime-owned: runId, workflowId, status, currentStepId, context map, stepOutputs map, pendingArtifact
- `WorkflowStatus` — enum: `RUNNING`, `PAUSED`, `AWAITING_INPUT`, `AWAITING_APPROVAL`, `COMPLETED`, `FAILED`
- `WorkflowEvent` — immutable append-only audit record
- `ContextValue` — sealed interface: `StringContextValue`, `NumberContextValue`, `BooleanContextValue`, `JsonContextValue`, `ContextValueList`

### Retry and loop concepts
- `RetryPolicy` — record with factory methods `none()` and `simple(int maxAttempts)`
- `LoopTerminationStrategy` — enum: `AGENT_SIGNAL`, `EVALUATOR`, `FIXED_COUNT`, `FOR_EACH`
- `MaxIterationsAction` — enum: `AWAIT_USER`, `FAIL`

---

## Filesystem Layout

```
agents/
  agentId.agent/
    agent.json        # agent definition
    systemprompt.md   # system prompt
    boundaries.md     # optional, appended to system prompt with two line separators

workflows/
  workflowId.workflow/
    workflow.json             # workflow definition
    blueprintId.blueprint.json  # zero or more blueprints
    artifactId.artifact.json    # zero or more artifacts
```

- Agent ids, workflow ids, blueprint ids, and artifact ids must match their directory or filename stems exactly.
- The config-loader validates this at load time and fails fast on mismatch.

---

## LLM Provider Model

- `LlmClient` — interface: `execute(LlmExecutionRequest)` returning `String`
- `LlmClientFactory` — registered via JPMS `ServiceLoader`, one factory per provider module
- `LlmClientImpl` — abstract, template method pattern: subclasses implement `buildHttpRequest` and `validateAndExtractResponse`
- `LlmExecutionRequest` — record: provider, nullable model, systemPrompt, userInput
- Agents return **structured JSON arrays of typed commands**: `CREATE_FILE`, `USER_PROMPT`, `SET_CONTEXT`, `RUN_COMMAND`, `COMPLETE`, `GENERATE_QUESTIONS`, `ESCALATE`

---

## Structured Output — Never Parse Free Text

Agents do not return prose that the runtime interprets. They return a JSON array of typed command objects. The runtime dispatches on command type to produce real-world side effects. If you are about to write code that parses or pattern-matches free text from an LLM response, stop — use the structured command model instead.

---

## Security — Path Traversal

All file loading code uses `Validate.requireWithinBase()` to prevent path traversal. Never construct a `Path` from user-supplied or config-supplied input without validating it stays within the expected base directory. This is enforced in `PromptLoader` and `FileSystemAgentLoader` and must be maintained in any new file loading code.

---

## What Is Complete — Do Not Redesign

These modules are complete and reviewed. Suggestions that change their architecture or public API are not welcome unless a bug is being fixed:

- ``

---

## Specialised Agent Prompts

For focused tasks, use the agent prompts in `.github/agents/`:

- **`@javadoc-agent`** — adds missing Javadoc to public API surface following project conventions
- **`@unit-test-agent`** — generates JUnit 5 unit tests with module-specific coverage guidance
- **`@review-agent`** — performs code review with module-specific guidance, leaving comments as `USER_PROMPT` commands for the developer to read and respond to
- **`@commit-agent`** — stage and write commit messages following Conventional Commits format, with staged file confirmation

These agents inherit the context from this file. Reference them for their specific task rather than repeating this context in your prompt.
