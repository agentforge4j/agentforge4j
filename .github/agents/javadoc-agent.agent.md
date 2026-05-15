# AgentForge4j — Javadoc Agent

Add or improve javadocs for module: "[MODULE_NAME]".

## Context

Read `.github/copilot-instructions.md` first. It defines the module structure, domain model, and coding standards. Match its terminology exactly.

---

## Purpose

Add Javadoc comments to the public API surface where they are missing or incomplete. Do not modify any logic, formatting, or imports. Do not touch tests.

---

## Scope

### Document these

- Every type, method, and constant exported by the module's `module-info.java`
- Public static factory methods (e.g. `RetryPolicy.simple(...)`, `ContextMapping.none()`)
- Sealed interfaces and each permitted subtype (`Executable`, `StepBehaviour`, `LlmCommand`, `ArtifactItem`, `ContextValue`)
- Records — document the type's purpose and invariants; use `@param` on the canonical constructor only when the meaning of a component is not obvious from its name
- Enums — document the type and each constant explaining when and why it is used (`WorkflowStatus`, `WorkflowEventType`, `AgentLocality`, `LoopTerminationStrategy`, `MaxIterationsAction`)
- Exception types in `com.agentforge4j.core.exception` — one sentence describing what situation causes them
- `Validate` methods and their `Supplier<RuntimeException>` overloads
- `BehaviourHandler` and `LoopStrategy` implementations — contract first, dispatch detail second

### Do not document these

- Types and members not exported by `module-info.java` — they are internal by design
- Accessor methods on records — document the record at type level; individual accessor Javadoc is redundant
- Lombok-generated constructors and getters on non-record classes
- Test classes and test helpers
- `package-info.java` files (handled separately if at all)

---

## Rules

### Content

- Explain the contract, not the implementation. Say what is guaranteed, what is validated, and what is thrown — not how it works internally.
- Never restate the method name in prose. `/** Gets the id. */` on `id()` is noise — leave it undocumented rather than write that.
- For interfaces, document the contract every implementation must honour.
- For sealed interfaces, mention the discriminator (Jackson `type` or `kind`) and reference the permitted subtypes via `{@link}`.
- For `WorkflowState`, be explicit about which fields are final, which are mutable via `@Setter`, and which maps are mutated directly by the runtime.
- For exception classes, one sentence describing what situation causes the exception is enough.
- For `Validate.*` methods, document the message format used in the thrown exception and the return value contract.
- If the purpose of something is genuinely unclear from the code, write a `TODO` inside the Javadoc block rather than guessing.
- Never fabricate behaviour you cannot confirm from the implementation.
- Never remove existing Javadoc — add to it or improve it.

### Format

- `/** Single sentence. */` on one line for simple cases.
- Multi-line format with tags on their own lines for anything more complex.
- `{@code ...}` for inline type and method references.
- `{@link ...}` to cross-reference related types where it genuinely aids understanding — do not overlink.
- No `@author`, `@version`, or `@since` tags.
- Present tense, third person singular: "Returns the agent definition", not "Return" and not "This method returns".
- No filler phrases: not "This class is responsible for", not "This method is used to".

---

## Output

Concise public API Javadocs only. Focus on contract, parameters, return value, validation behaviour, and thrown exceptions. Avoid marketing language and repeated project goals. Do not explain obvious implementation steps.
