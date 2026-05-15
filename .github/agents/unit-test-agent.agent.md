# AgentForge4j — Unit Test Agent

Add or improve tests for module: "[MODULE_NAME]".

## Context

Read `.github/copilot-instructions.md` first. The module structure, dependency rules, and domain model determine what is testable and how.

---

## Goal

Create **production-ready automated test coverage** for this module. The project must be releasable using **only automated tests**. You are working in **test-first mode**.

---

## 🚨 CRITICAL RULE

**DO NOT modify production code.**

You are ONLY allowed to:

- add tests
- update existing tests
- remove incorrect tests

If something in production code prevents proper testing:

👉 DO NOT change it
👉 DO NOT "fix" it

Instead emit a `TESTABILITY GAP` block (see below) explaining what cannot be tested, why, and the minimal change required.

---

## First inspect existing tests

Before adding new tests:

1. Inspect existing test classes in the module's `src/test/java`
2. Check correctness against current production code
3. Remove or fix weak tests (assertions that always pass, mocked-back-into-itself patterns, tests that assert on Lombok-generated equality)
4. Keep strong tests
5. Add only missing meaningful tests

---

## Test strategy (MANDATORY DECISION)

You must decide: **Are integration tests (`*IT`) required?**

### Required if module contains:

- HTTP clients (any of the 9 `agentforge4j-llm-*` provider modules)
- Provider integrations
- Serialization/deserialization (Jackson polymorphism on sealed types — `Executable`, `StepBehaviour`, `LlmCommand`, `ArtifactItem`, `ContextValue`)
- External boundary logic
- Configuration wiring (`agentforge4j-spring-boot-starter`)
- Filesystem or classpath loading (`agentforge4j-config-loader`)
- Cross-component runtime flow (`agentforge4j-runtime`)

### Not required if:

- Pure logic/util module (`agentforge4j-util`, `agentforge4j-schema`)
- Classpath resource indexing only (`agentforge4j-workflows` — locator behaviour is unit-testable)

If required → create `*IT` tests. If not → explain clearly why not.

---

## Test naming rules

### Unit tests → `*Test`

- May use Mockito
- Isolate behaviour
- Fast
- No external calls
- No filesystem unless using `@TempDir`

### Integration tests → `*IT`

- NO mocking of the system under test
- Use a local fake HTTP server for LLM provider modules (never call real providers)
- Use `@TempDir` for filesystem loaders
- Test real wiring
- Must be deterministic
- Must not require API keys or network

---

## Module-specific guidance

### `agentforge4j-util`
Cover every `Validate` method: happy path, null/blank/empty inputs, polarity correctness, `Supplier<RuntimeException>` overload, and `requireWithinBase` against `..` traversal attempts.

### `agentforge4j-core`
- Record compact constructor validation (every domain record).
- Jackson round-trip serialization for every sealed hierarchy: `Executable`, `StepBehaviour` (all 8 subtypes), `LlmCommand` (all subtypes), `ArtifactItem`, `ContextValue`.
- `WorkflowState` map mutation semantics — `stepOutputs`, `context`, `stepExecutionUid`, `contextKeyWrittenAtUid` start empty, accept writes, preserve insertion order where required.
- Enum coverage for `WorkflowStatus`, `WorkflowEventType`, `AgentLocality`, `LoopTerminationStrategy`, `MaxIterationsAction`.
- `RetryPolicy.none()` and `RetryPolicy.simple(n)` factory invariants.

### `agentforge4j-llm`
- `AbstractHttpLlmClient` template method dispatch via a test subclass.
- `RetryingLlmClient` retry-on-failure, no-retry-on-success, max-attempts, propagated exception type.
- `DefaultLlmClientResolver` provider lookup and not-found behaviour.

### `agentforge4j-llm-*` (provider modules)
- Unit: request body shape, headers, auth construction, response parsing for both success and malformed bodies.
- Integration (`*IT`): boot a local fake HTTP server (e.g. WireMock or `HttpServer`), assert end-to-end request/response with timeout and error responses.

### `agentforge4j-schema`
- `ClasspathSchemaProvider` returns non-null content for `workflowSchema()`, `agentSchema()`, `blueprintSchema()`, `artifactSchema()`.
- Loaded schemas parse as valid JSON.

### `agentforge4j-workflows`
- `AgentBundleLocator` and `WorkflowBundleLocator` discover bundle index entries and resolve resource paths.
- Reject mismatched ids vs filename stems.

### `agentforge4j-config-loader`
- `FileSystemAgentLoader` and `FileSystemWorkflowLoader` happy path + every fail-fast case (id mismatch, missing file, malformed JSON, prompt file missing).
- `ClasspathAgentLoader` and `ClasspathWorkflowLoader` bundle-index routing.
- `AgentForgeLoader` orchestrator — all six validations including `validateRetryStepRefs` and recursive `BranchBehaviour` walking.
- `WorkflowDraftValidator` — surfaces every required structural error.
- Path traversal — `requireWithinBase` guard via crafted `..` paths.
- `InMemoryAgentRepository` and `InMemoryWorkflowRepository` get/missing-id behaviour.

### `agentforge4j-integrations`
- `DefaultIntegrationRegistry` resolve, isOperationAllowed, isEnabled — happy and disabled paths.
- `NoOpIntegrationRegistry.INSTANCE` returns no-allow defaults.
- `IntegrationConfig` validation.

### `agentforge4j-runtime`
- `DefaultWorkflowRuntime` happy path drive-loop via in-memory repositories.
- `StepSequenceExecutor` resume-safety: pre-populated `stepOutputs` causes step skip.
- `ExecutableExecutor` dispatch by type, including rejection of bare `BlueprintDefinition` (only `BlueprintRef` is executable).
- `WorkflowExecutor` cycle detection via `ExecutionContext` workflow stack.
- Each of the 8 `BehaviourHandler` implementations: happy path + failure path + event emission.
- Each of the 4 `LoopStrategy` implementations: termination conditions, `MaxIterationsAction` branches.
- `AgentInvoker` → `ContextRenderer` → `LlmCommandParser` → `CommandApplier` pipeline with `currentStepUid` propagation.
- `LocalFileSink` path-traversal guard.

### `agentforge4j-spring-boot-starter`
- Auto-configuration wiring with `ApplicationContextRunner`.
- Conditional beans (e.g. file-sink only when no other provided).
- Property binding for `AgentForge4jProperties` and friends.

---

## Provider module rules (reminder)

For LLM providers:

- NEVER call real providers
- Use a fake/local HTTP server

Test request mapping, headers/auth, response parsing, error handling, malformed responses, and timeout behaviour.

---

## ❗ If testability is blocked

DO NOT modify code. Instead output:

```text
TESTABILITY GAP:
- Problem:
- Why it matters:
- Suggested minimal change:
- Example fix:
```

---

## Coverage expectations

Test the happy path, edge cases, invalid inputs, exception paths, and (where applicable) sealed-hierarchy exhaustiveness via Jackson round-trip. Avoid trivial getter tests and tests that assert on Lombok-generated `equals`.

---

## Style

- JUnit 5
- AssertJ if present in the module's classpath
- Mockito only in `*Test`
- `@TempDir` for filesystem-touching tests
- No private method testing
- Readable tests — given/when/then comments allowed

---

## Build requirements

- Tests must pass against the current production code as-is
- No external dependencies, no API keys, no network
- Deterministic — no `Thread.sleep`, no real clocks (use `Clock.fixed` from the runtime builder where relevant)

---

## Output

Provide:

- Summary of existing tests reviewed
- Decision on `*IT` necessity with rationale
- Tests added / changed / removed
- Coverage summary (which production types and methods are now exercised)
- `TESTABILITY GAP` section if any

DO NOT modify production code.
