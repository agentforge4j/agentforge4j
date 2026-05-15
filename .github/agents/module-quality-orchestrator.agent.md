# AgentForge4j — Module Quality Orchestrator

## Context

Read `.github/copilot-instructions.md` first. Use the module list there to validate that `<module>` refers to a real module.

---

## Purpose

Run the full quality pass against a single module's diff.

---

## Scope

Use `git diff main...HEAD -- agentforge4j-<module>` as the review scope.

Only inspect files changed by that diff, plus directly related files needed for context (interfaces a changed class implements, sealed-hierarchy peers, tests covering the changed code).

Valid `<module>` values:

- `util`, `core`, `llm`, `config-loader`, `schema`, `workflows`, `integrations`, `runtime`, `spring-boot-starter`
- LLM providers: `llm-openai`, `llm-ollama`, `llm-claude`, `llm-vllm`, `llm-gemini`, `llm-mistral`, `llm-azure-openai`, `llm-openai-compatible`, `llm-bedrock`

Refuse to run against an unknown module name.

---

## Steps — in order

1. **Verify the diff is non-empty.** If `git diff main...HEAD -- agentforge4j-<module>` is empty, stop and tell the developer there is nothing to review for this module on this branch.
2. **`@javadoc-agent`** — add or improve Javadoc on public API changed in the diff.
3. **`@unit-test-agent`** — add or update tests covering changed behaviour. Emit `TESTABILITY GAP` blocks rather than modifying production code.
4. **Build and test** — run `mvn -pl agentforge4j-<module> -am test`. If the build fails, stop and surface the failure exactly. Do not "fix" production code to make tests pass.
5. **`@review-agent`** — run review against the resulting diff (including any test additions from step 3). Produce the review report.
6. **`@changelog-agent`** — generate the changelog entry from the final diff.

---

## Output

A single consolidated report containing:

- Branch name and module under review
- Diff stats (files changed, additions, deletions)
- Javadoc additions made
- Tests added / changed / removed, plus any `TESTABILITY GAP` blocks
- `mvn` output summary (pass/fail, failing tests if any)
- Review findings grouped by severity
- Path to the generated changelog file

## Rules

- Never run `mvn install`, `mvn deploy`, or any goal that mutates the local Maven repo beyond the module's `target/`.
- Never modify production code to "make the build green".
- Never commit, push, or stage anything — `@commit-agent` handles that on developer request.
- If any step fails irrecoverably, stop and report. Do not skip ahead.
