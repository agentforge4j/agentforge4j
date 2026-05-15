# AgentForge4j — Changelog Agent

## Context

Read `.github/copilot-instructions.md` first so module scopes are accurate.

## Goal

Review all current changes and create a concise changelog file. You do not modify source code, tests, or any production file.

## Inputs

Look at:

- staged changes
- unstaged changes
- new files
- deleted files
- renamed files

Only describe real changes — no guessing, no marketing language, no fabricated motivation.

---

## Output file

Create or update:

```
docs/changelogs/changelog-<branch-name>-<YYYY-MM-DD>.md
```

Replace `<branch-name>` with the output of `git branch --show-current` (slashes become hyphens). Replace `<YYYY-MM-DD>` with today's date.

If the file already exists for this branch and date, update it in place rather than creating a duplicate.

## Format

```
# Pending Changelog

## Summary
Short description of what changed (1–3 sentences).

## Changes
- Added ...
- Updated ...
- Removed ...
- Fixed ...

## Modules Affected
- agentforge4j-<name>
- agentforge4j-<name>

## Suggested Commit Message
<type>(<scope>): <short imperative summary>

## Suggested Pull Request Description

### Summary
...

### Changes
...

### Notes
...
```

Use the same `<type>` and `<scope>` rules as the commit-agent. Valid module scopes: `util`, `core`, `llm`, `config-loader`, `schema`, `workflows`, `integrations`, `runtime`, `spring-boot-starter`, and the nine LLM provider scopes (`llm-openai`, `llm-ollama`, `llm-claude`, `llm-vllm`, `llm-gemini`, `llm-mistral`, `llm-azure-openai`, `llm-openai-compatible`, `llm-bedrock`).

---

## Rules

- Be concise.
- Group related changes.
- No fluff, no repetition.
- Warn at the top of the file if the commit is too large or mixes unrelated concerns.
- Treat `*Test` and `*IT` changes as `test` changes, not `feat`.
- Treat changes touching only `.github/agents/*`, `README.md`, or `docs/**` as `docs` or `chore` — never `feat`.

## Important

Only create or update the file above. Do not edit any other files.
