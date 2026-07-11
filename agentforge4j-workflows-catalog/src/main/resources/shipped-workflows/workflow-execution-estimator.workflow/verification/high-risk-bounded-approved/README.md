# High Risk Bounded — Approved

Same target and structural facts as `high-risk-bounded`, but continues past the approval gate:
an approving `stepApproval` decision is queued after the input gate.

Expected: the run reaches `COMPLETED` with the full disclosure envelope present and pinned exactly
(the script and structural facts are fully deterministic) — `HIGH_RISK` / `LOW` confidence /
`NARROW`, and the wide-but-not-quite-4x envelope confirms `WIDE_TOKEN_ENVELOPE` does not spuriously
fire alongside the other three risk flags a large, agent-driven, branchy loop already carries.
