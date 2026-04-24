# AgentForge4j — Unit Test Agent

## Context

Read `.github/copilot-instructions.md` first. It defines the module structure, domain model, dependency rules, and coding standards. Everything in that file applies here.

---

## Purpose

Generate JUnit 5 unit tests for code that has no test coverage. Generated tests are a first draft — a developer must review and harden them before they are considered complete.

---

## Non-Negotiable Rules

- **JUnit 5 only.** Use `org.junit.jupiter.api.*` throughout. No JUnit 4 annotations.
- **No empty tests.** Every `@Test` method must contain at least one meaningful assertion.
- **No tests that only assert no exception.** `assertDoesNotThrow` with nothing else is not acceptable.
- **No unnecessary mocking of records.** Records are immutable value objects — construct them directly.
- **Tests live in `src/test/java` inside the same Maven module** as the class under test. Match the production package exactly.
- **Test class naming:** `{ClassName}Test`.
- **No `var` keyword.** Explicit types throughout.
- **Braces always required** on all control flow, even single-line bodies.

---

## What to Test

Look at the module's `module-info.java` to identify the exported public API. Focus tests on:

- **Compact constructor validation on records** — every field that is validated should have a test for the failing case and the passing case.
- **Static factory methods** — test what they produce and any constraints they enforce.
- **Interface contracts** — test that implementations honour the contract documented on the interface, including exceptions thrown.
- **Boundary conditions** — empty collections, null inputs where permitted, minimum and maximum values.
- **Filesystem operations** — use JUnit 5 `@TempDir` and build the directory structure in `@BeforeEach`.
- **Both overloads** where a method has a `String message` overload and a `Supplier<RuntimeException>` overload — test both.

---

## Structure

Use `@Nested` classes to group related scenarios. Use descriptive method names in `should_doX_when_Y` format.

Create a `TestFixtures` class in the test source root of the module for shared builder helpers and constants. Keep individual test classes readable — extract repeated construction into fixtures.

---

## What the Reviewing Developer Must Do

- Confirm every assertion is testing the right thing, not just mirroring the implementation.
- Add edge cases the agent missed.
- Remove any test that compiles but provides no real value.
- Verify `@TempDir` tests do not leak state between test methods.
- Add descriptive failure messages using AssertJ's `.as("explanation")` where the default JUnit message would be cryptic.