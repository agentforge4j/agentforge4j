# Agent Creator

The `agent-creator` workflow turns a freeform agent idea into an approved, validated, downloadable agent bundle.

## Flow

`collect-intent` → `structure-requirements` → `clarify` (conditional) → `assess` → `resolve-tier` (deterministic) →
`design-preview` → `estimate-tokens` (**approval gate**) → `generate-agent` → validate → `generate-verification` →
validate → `review` → verdict.

- **Approval gate.** No files are written before the human approves the design preview and token estimate.
- **Deterministic tier.** `recommendedTier` is resolved by a nested `BRANCH`/`ASSIGN_CONTEXT` policy on
  `sensitivityFloor`, `complexity`, and `risk` — never by an LLM. It is written into the generated `agent.json`
  `modelTier` and verified deterministically.
- **Deterministic validation.** The generated bundle is validated by the built-in `agent-bundle` validator after
  `generate-agent` (it must load as a valid agent definition, with a `modelTier == recommendedTier` equality
  contract), and by the `agent-creator-bundle` validator after `generate-verification` (the same checks plus the
  verification starter's structural shape), each against an exact file allowlist and an `id == agentId` equality
  contract.
- **Tokens only.** The estimate is in tokens; there is no monetary concept (OSS layer).

## Output bundle

The generated bundle — `agent.json`, `systemprompt.md`, `README.md`, and a `verification/` adaptation starter for
the created agent — is written as an **export package** under `shipped-agents/generated.agent/`, never as a live
catalog write: a running workflow has no mechanism to write into the deployed classpath catalog. The generated
`README.md` carries the install instructions (rename `generated.agent/` to `<agentId>.agent/`, copy it into the real
catalog tree, add it to `shipped-agents/index`) for a human to perform out of band.

See `agentforge4j-examples/workflow-catalog-examples/agent-creator/` for a real, runnable example that drives this
workflow end to end.
