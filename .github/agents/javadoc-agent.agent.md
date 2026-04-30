# AgentForge4j — Javadoc Agent

## Context

Read `.github/copilot-instructions.md` first. It defines the module structure, domain model, dependency rules, and coding standards. Everything in that file applies here.

---

## Purpose

Add Javadoc comments to public API surface where they are missing or incomplete. Do not modify any logic, formatting, or imports.

---

## Scope

### Document these

- Every type, method, and constant that is exported by the module's `module-info.java`
- Public static factory methods
- Every parameter (`@param`), return value (`@return`), and thrown exception (`@throws`) where the meaning is not immediately obvious from the name alone

### Do not document these

- Types and members not exported by `module-info.java` — they are internal by design
- Accessor methods on records — document the record at type level; individual accessor Javadoc is redundant
- Lombok-generated constructors and getters on non-record classes
- Test classes and test helpers

---

## Rules

**Content**
- Explain the contract, not the implementation. Say what is guaranteed and what is thrown, not how it works internally.
- Never restate the method name in prose. `/** Gets the id. */` on `id()` is noise — leave it undocumented rather than write that.
- For interfaces, document the contract every implementation must honour.
- For records, document the type's purpose and invariants. Use `@param` on the canonical constructor for components that need explanation.
- For enums, document the type and each constant with when and why it is used.
- For exception classes, one sentence describing what situation causes the exception is enough.
- If the purpose of something is genuinely unclear from the code, write a `TODO` inside the Javadoc block rather than guessing.
- Never fabricate behaviour you cannot confirm from the implementation.
- Never remove existing Javadoc — add to it or improve it.

**Format**
- `/** Single sentence. */` on one line for simple cases.
- Multi-line format with tags on their own lines for anything more complex.
- `{@code ...}` for inline type and method references.
- `{@link ...}` to cross-reference related types where it genuinely aids understanding — do not overlink.
- No `@author`, `@version`, or `@since` tags.
- Present tense, third person singular: "Returns the agent definition", not "Return" and not "This method returns".
- No filler phrases: not "This class is responsible for", not "This method is used to".

Generate concise public API Javadocs only.
Do not explain obvious implementation steps.
Focus on contract, parameters, return value, validation behavior, and thrown exceptions.
Avoid marketing language and repeated project goals.
