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
- **Deterministic validation.** The generated bundle is validated by the built-in `agent-bundle` validator (it must
  load as a valid agent definition), an exact file allowlist, and a `modelTier == recommendedTier` equality contract.
- **Tokens only.** The estimate is in tokens; there is no credit or currency concept (OSS layer).

## Output bundle

`agent.json`, `systemprompt.md`, `README.md`, and a `verification/` adaptation starter for the created agent.

See `examples/pdf-summarizer/` for a canonical example of a bundle this workflow produces.
