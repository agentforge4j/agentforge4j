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

### Front-end packages

The TypeScript packages (`agentforge4j-workflow-builder`, `agentforge4j-web-ui`,
`agentforge4j-ui-e2e`) live outside the Maven reactor:

- **Node 24** (see `.nvmrc`) and `npm ci` per package.
- Builder changes: run `npm run typecheck` and `npm test` in `agentforge4j-workflow-builder`.
  PRs touching the builder or the E2E package must also pass the `builder` CI check
  (Playwright end-to-end tests).

### Examples tree

`agentforge4j-examples` is a standalone Maven tree, not a reactor module. It resolves the
framework SNAPSHOT from your local repository — run `./mvnw install -DskipTests` at the
repository root first, then `./mvnw verify` inside `agentforge4j-examples`.

## Pull request workflow

1. **Branch per change** off `main` (e.g. `fix/...`, `feature/...`).
2. Keep changes focused and additive; avoid unrelated refactors in the same PR.
3. Add or update tests for any change in behaviour.
4. Ensure `./mvnw verify` passes.
5. Open a PR to `main` with a clear description of what changed and why. Fill in the PR template.
6. CI must be green — the `quality-gate` check (build matrix + Sonar quality gate) is required
   before a PR can merge.

Maintainers review and merge; please don't merge your own PR.

## Releases

> **How releases work.** `main` is always the development line for the next release. Large
> experimental work lives on `initiative/*` branches and merges into `main` only once accepted.
> A release is a Git tag with a track prefix — `framework-v0.2.0` for the framework, `catalog-v*`
> for the shipped-workflow catalog, `builder-v*` for the npm builder package; pushing a tag
> triggers automated verification and publishing, and the tagged tree contains its own release
> notes and changelog entry. Bug fixes for a released version land on a `release/<track>-0.N.x`
> maintenance branch and ship as a patch tag; the same fix then reaches `main` through a reviewed
> forward-port PR. Only the latest minor of each track is supported before 1.0.0. Track version
> numbers are independent — what works together is defined by the workflow **schemaVersion** (a
> required field in every workflow definition, incremented on every format change); see the
> compatibility matrix in the docs. Report bugs against the latest release of the relevant track
> (use the component field in the issue template).

Three release tracks, independently versioned:

| Track | Tag prefix | Where it publishes |
|---|---|---|
| Framework (this Maven reactor) | `framework-v*` | Maven Central |
| Shipped workflow catalog | `catalog-v*` | Maven Central |
| Workflow builder | `builder-v*` | npm (`@agentforge4j/workflow-builder-react`) |

**Patch flow.** A fix for an already-released version is applied on that version's
`release/<track>-0.N.x` branch, not by cherry-picking onto `main`. The same fix then reaches
`main` through a reviewed forward-port pull request — reuse the fix commit when the branches
haven't diverged, adapt it in the PR when they have, with equivalent regression coverage either
way. Direct or unreviewed cherry-picks are never used.

**Which version do I report against?** Each track supports only its latest minor before 1.0.0 —
see [SECURITY.md](SECURITY.md) for the full policy. When filing an issue, use the component
dropdown in the issue template to say which track you mean, and report against that track's
latest release; an older minor is already end-of-life and won't receive a fix directly (only a
forward-port to the current line). See [`docs/releases/README.md`](docs/releases/README.md) for
the maintainer's own release checklist, and the per-track `CHANGELOG.md` files for release
history.

## Architecture decision records

Significant architectural decisions are recorded as ADRs in [`docs/adr/`](docs/adr/README.md).
Copy [`docs/adr/TEMPLATE.md`](docs/adr/TEMPLATE.md) to start one.

**Create an ADR when a change:**
- shapes architecture, the extension/SPI model, runtime behavior, or the governance model;
- changes a public contract — the workflow schema, an SPI signature, the event/status set;
- affects compatibility or would be hard/breaking to reverse later;
- sets project direction in a way future contributors would otherwise have to reverse-engineer
  from git history.

**Don't create an ADR for:**
- an internal refactor with no externally visible effect;
- a naming or code-style choice;
- a bug fix, merely because the diff is large — size alone isn't the test. A fix needs an ADR
  only if it introduces or changes a durable architectural decision or public contract (a
  retry-semantics change or an extension-contract correction is architectural regardless of
  diff size; a large but purely mechanical fix is not);
- something already covered by an existing ADR — extend or supersede that one instead of
  starting a parallel record on the same topic.

**ADRs vs. design documents:** a design document is the full working material — forces, phased
plan, open questions worked through in detail. An ADR is the compact, durable record of what
the design concluded, written once the decision is settled (or, for a Proposed ADR, once the
direction is settled enough to commit to in writing). A Proposed ADR keeps a **Verification
note**: what must be true on `main` for it to become Accepted, and an explicit **Open
questions** section for anything genuinely still unresolved — don't let unresolved details
hide inside prose.

**ADRs vs. implementation PRs:** the PR does the work; the ADR records the decision the work
implements. For new architectural work, write the Proposed ADR alongside or before the
implementing PR — a PR description may reference the relevant ADR number for context. **Status
promotion to Accepted is never part of the implementing PR.** The implementing PR merges with
the ADR still marked Proposed; once it's actually on `main`, open a small follow-up PR that
promotes the status to Accepted, confirming the cited class, module, or behaviour is really
there. A PR can't truthfully assert its own merge state about itself before it merges — and a
fully reviewed, "ready" stack is not the same as merged. Don't let that follow-up drift stale
once the prerequisite is actually met.

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
