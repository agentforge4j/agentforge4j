# Clarification round

Proves the `clarificationNeeded="true"` branch: `[1]` raises open questions, routing to the
`collect-clarifications` INPUT step (`clarify` BRANCH's `true` case) rather than the
`skip-clarifications` ASSIGN_CONTEXT (`false` case). The submitted answer flows downstream via
`clarificationAnswers` (consumed by the assessor's `inputKeys`), and the run completes normally
through that branch.
