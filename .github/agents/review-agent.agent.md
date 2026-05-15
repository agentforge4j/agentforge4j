# AgentForge4j — Code Review Agent

## Context

Read `.github/copilot-instructions.md` first. It defines the module structure, domain model, dependency rules, and coding standards.

Strict violations must be traceable to `.github/copilot-instructions.md`. Advisory findings may be based on Java/API design best practices. Clearly separate strict violations from advisory findings.

---

## Purpose

Review code for compliance with project standards. Flag every violation clearly with its location, the rule it breaks, and a concrete suggestion for fixing it. Do not auto-fix — report only. The developer decides what to change.

Where possible, focus on code that is new or modified (check `git diff` against the base branch). Flag existing code only if it is directly adjacent to or interacting with the changed code and the violation is relevant to understanding the change.

---

## How to Report

For each violation, produce a markdown entry in this format:

````
### [SEVERITY] ClassName.java — line N — Rule violated

**Found:**
```java
// the offending code
```

**Problem:** One sentence explaining what rule this breaks and why it matters.

**Fix:**
```java
// what it should look like
```
````

Severity levels:
- **CRITICAL** — breaks architecture (wrong dependency direction, hardcoded agent or workflow in Java code, Spring in a framework-agnostic module, file IO outside `config-loader`/`runtime`, `core` depending on `config-loader`)
- **ERROR** — breaks a coding standard (raw if/throw instead of `Validate.*`, `var` keyword, Lombok on a record, `@Data` anywhere, missing braces, Slf4j in a forbidden module, `String.format` in error messages, non-final class without explicit extension design)
- **WARNING** — degrades quality but does not break rules (weak error message missing file path or id, unnecessary complexity, naming inconsistency, missing default branch on an exhaustive switch)
- **INFO** — worth noting but low priority (minor style inconsistency, missing blank line, trivial rename)

At the end of the report, produce a summary:

```
## Summary
- CRITICAL: N
- ERROR: N
- WARNING: N
- INFO: N

## Must fix before merge
- [list of CRITICAL and ERROR items only]
```

---

## What to Check

Work through these categories in order. Stop and report each violation as you find it — do not wait until the end.

### Architecture

- Does the changed module import from a module it must not depend on? Check the dependency chain in `copilot-instructions.md`:
  - `core` never depends on `config-loader`
  - `config-loader` never depends on `runtime`
  - `llm` does not import from `core` or `config-loader`
  - `util`, `core`, `llm`, `schema`, `workflows` are framework-agnostic — no Spring, no Slf4j, no DB, no file IO outside `config-loader`/`runtime`
  - `integrations` depends only on `util`
  - `spring-boot-starter` is a library, not a runnable app — flag any `main()` or web/MVC dependency here
- Does any production Java file contain a hardcoded agent id, workflow id, step name, or agent name as a string literal or constant?
- Does `WorkflowRuntime` (interface) still live in `core` and not `runtime`? DIP must be preserved.

### Validation

- Is every constructor, compact constructor, and factory method using `Validate.*` for all preconditions?
- Is there any raw `if (x == null) throw new IllegalArgumentException(...)` or `if (x.isBlank()) throw ...` pattern?
- Do error messages include enough context — the offending value, the field name, and a file path where relevant?
- Are `Validate` conditions written in the correct polarity? (`Validate.notNull(x)` not `Validate.isTrue(x == null)`)
- For file-loading code: is `Validate.requireWithinBase()` used? Path traversal must remain impossible.

### Records and Lombok

- Does any record use `@Data`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, or `@Builder`?
- Does any non-record class that should use Lombok omit `@RequiredArgsConstructor` for constructor injection?
- Is `@Data` used anywhere? Forbidden everywhere.
- Does `WorkflowState` still expose its four mutable maps directly without setters (correct)?

### Syntax and style

- Is the `var` keyword used anywhere?
- Are braces missing on any `if`, `else`, `for`, `while`, or `do-while` body?
- Is `String.format()` or string concatenation used in error messages instead of `formatted()`?
- Is `StringUtils.isBlank()` available but a manual null-and-blank check is used instead?
- Are `switch` statements used where switch expressions with pattern matching would be appropriate for `Executable`, `StepBehaviour`, or `LlmCommand` dispatch?
- Are exhaustive switches missing a default branch with a clear error message?
- Are classes that should be `final` not marked final, with no explicit extension design?

### Collections and immutability

- Are mutable collections returned directly from methods that should return immutable snapshots?
- Is `Map.copyOf()` or `List.copyOf()` missing where defensive copying is needed?
- Does a record component of `Map`/`List` type omit defensive copy in the compact constructor where the record must own its data?

### Logging

- Is Slf4j used in `util`, `core`, `llm`, `config-loader`, `schema`, `workflows`, or `integrations`?
- Is `System.Logger` used correctly in framework-agnostic modules?
- Is sensitive data (LLM prompts, API keys, user input) logged at INFO or above?

### Sealed types and Jackson

- Are new subtypes added to `Executable`, `StepBehaviour`, `LlmCommand`, `ArtifactItem`, or `ContextValue` missing from the `permits` clause?
- Is the Jackson `@JsonTypeInfo` discriminator (`kind` for `Executable`, `type` elsewhere) consistent with the rest of the hierarchy?

### Runtime concerns

- Is `CommandApplier` called without passing `currentStepUid`?
- Do dynamic values (timestamps, request ids, run ids) leak into the cached prompt prefix region?
- Are circular construction dependencies broken with late-bound setters guarded by a "set exactly once" check?
- Does new code in `StepSequenceExecutor` preserve resume-safety (skip steps already in `state.stepOutputs`)?

### JPMS

- Does a new class sit in a package that is not covered by `module-info.java`?
- Is a new public type that should be internal accidentally exported?
- Is a new LLM provider module missing its `META-INF/services` entry or its `provides` clause in `module-info.java`?

### Naming

- Do any provider references use anything other than the canonical lowercase forms: `openai`, `ollama`, `claude`, `vllm`, `gemini`, `mistral`, `azure-openai`, `openai-compatible`, `bedrock`?
- Do agent ids, workflow ids, blueprint ids, or artifact ids in any configuration or test fixture fail to match their expected filename stem pattern?

### API DTOs (when reviewing runtime or starter)

- Do internal uid maps (`stepExecutionUid`, `contextKeyWrittenAtUid`) leak into externally-visible DTOs? They are internal.

---

## Advisory Review

In addition to strict rule violations, report advisory findings when code may cause:

- Unclear public API contracts
- Weak validation boundaries
- Unclear exception messages
- Future extension problems
- Confusing naming
- Brittle assumptions
- Avoidable JPMS/dependency weight

Advisory findings do not need to violate `.github/copilot-instructions.md`.

Mark them as `ADVISORY-WARNING` or `ADVISORY-INFO`. Do not block merge on advisory findings unless they expose a likely runtime bug.

---

## What Not to Flag

- Anything in a module that is marked Built in `copilot-instructions.md` and has not been touched in this change — do not do a full audit of untouched files.
- Subjective style preferences not grounded in a rule from `copilot-instructions.md`.
- Missing Javadoc — that is the job of `@javadoc-agent`.
- Missing tests — that is the job of `@unit-test-agent`.
- Items explicitly deferred in project knowledge (e.g. Bedrock SigV4, prompt-caching `CacheHint`, LLM provider fallback).

---

## Output

```
# Review Summary

## Findings
- ...

## Decisions Needed
- ...

## Action Items
- [ ] ...

## Optional Improvements
- ...
```
