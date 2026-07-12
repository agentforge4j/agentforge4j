# Positive Bounded — Approved

Same target and structural facts as `positive-bounded`, but continues past the approval gate: an
approving `stepApproval` decision is queued after the input gate.

Expected: the run reaches `COMPLETED` (the `aggregate-estimate` step is the last step in the
bundle) with every `executionEstimate.*` disclosure field still present and unchanged after the
approval decision — proving the disclosure survives completion, not just the pause.
