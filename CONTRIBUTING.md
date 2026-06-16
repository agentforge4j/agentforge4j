# Contributing to AgentForge4j

Thanks for your interest in contributing! AgentForge4j is an open-source Java framework for
structured, predictable multi-agent AI workflow orchestration. This guide covers how to propose
changes and the conventions the project expects.

By participating you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

> **Status:** AgentForge4j is in active development and pre-1.0. APIs and conventions may still
> change. Open an issue to discuss anything substantial before investing significant effort.

## Ways to contribute

- **Report a bug** or **request a feature** via the issue templates (Issues → New issue).
- **Improve documentation.**
- **Submit code** via a pull request, following the workflow below.

For anything beyond a small fix, please open an issue first so the approach can be agreed before
you write code.

## Development setup

- **JDK 17** is the project baseline. Install a JDK 17 distribution (e.g. Temurin). CI also
  verifies the build on JDK 21.
- Use the bundled **Maven Wrapper** — no separate Maven install is needed:
  - Linux/macOS: `./mvnw verify`
  - Windows: `mvnw.cmd verify`
- A full `verify` runs the unit tests, integration tests (`*IT`), and the gating SpotBugs,
  Checkstyle, and licence-header checks. Get it green locally before opening a PR.

## Pull request workflow

1. **Branch per change** off `main` (e.g. `fix/...`, `feature/...`).
2. Keep changes focused and additive; avoid unrelated refactors in the same PR.
3. Add or update tests for any change in behaviour.
4. Ensure `./mvnw verify` passes.
5. Open a PR to `main` with a clear description of what changed and why. Fill in the PR template.
6. CI must be green — the `quality-gate` check (build matrix + Sonar quality gate) is required
   before a PR can merge.

Maintainers review and merge; please don't merge your own PR.

## Coding conventions

The codebase follows a consistent style. New code is expected to match it:

- **Java 17 only in `main` sources** — do not use Java 21+ APIs or language features. The
  JDK 17/21 build matrix exists to enforce this.
- **Licence header on every Java file.** Each `.java` file (including `module-info.java`) must
  begin with exactly:
  ```java
  // SPDX-License-Identifier: Apache-2.0
  ```
  The build fails on a missing header. Run `./mvnw license:format` to add it automatically.
- **No `var`** in production code (tests may use it). Use explicit types.
- **Braces on all control-flow statements**, even single-line.
- `final` on classes not designed for extension; **records** for immutable domain types and DTOs.
- Use `String.formatted(...)` rather than string concatenation for formatted messages.
- Validate arguments with the project's `Validate.*` helpers, chosen by intent
  (`notBlank`, `notNull`, `isGreaterThanZero`, `requireWithinBase`, …).
- **Javadoc** on public and protected members.
- **Logging:** use `System.Logger` (the framework is logging-backend agnostic) — not SLF4J.
- **Modules:** each module's `module-info.java` exports only its public package(s). Respect the
  module dependency direction; don't introduce new cross-module dependencies casually.
- **No hardcoded agents or workflows in Java** — agent and workflow definitions live in external
  JSON/markdown configuration.

## Commits

- Write clear, descriptive commit messages explaining the change (imperative mood, e.g.
  "Add retry policy to LLM client").
- Group related work; avoid noise commits in the final history where practical.

## Reporting security issues

Do **not** open a public issue for a security vulnerability. See [SECURITY.md](SECURITY.md) for
how to report privately.

## Licence

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the project's licence.
