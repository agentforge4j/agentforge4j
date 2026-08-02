# SDLC Epic Package

Estimates the likely execution shape of building the full AI Agent Adoption Center application from
an Epic Creator-style epic breakdown (Mode 2) — twelve epics, each carrying its own bounded rework
loop through the assumed per-epic phase pipeline (Architecture, Test Strategy, Development, Security
& Delivery Readiness), plus one final aggregate delivery-package phase.

The caller resolves `mode: SDLC_EPIC_PACKAGE` and supplies the structural summary produced by
analysing the epic package, not a single workflow definition — this workflow's own routing does not
change: the same `route-on-ceiling` and `estimate` steps serve both modes, since a package this large
is still bounded (`ceilingDerivable` stays `true`) and only widens the envelope and raises risk.

Expected: reaches `AWAITING_STEP_APPROVAL` with `HIGH_RISK` complexity (twelve epics exceeds the
large-package threshold), the epic-driven risk flags, and the full disclosure present — the
recommendation this feeds is intended to inform the Full Application SDLC workflow's own
human-approval gate before Architecture begins (a downstream integration, out of this bundle's scope).
