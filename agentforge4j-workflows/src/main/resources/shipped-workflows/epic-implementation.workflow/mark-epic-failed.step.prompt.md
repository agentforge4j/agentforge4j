## Step: Mark Epic Failed

Retry attempts have been exhausted. The epic could not be brought to SUCCESS within the allowed attempts.

### Output

Two `SET_CONTEXT` commands:

- `epicStatus` as a StringContextValue with the exact value `"FAILED"`
- `epicNotes` as a JsonContextValue preserving the prior `epicNotes` content and adding `{"finalDecision": "FAILED", "reason": "Retry attempts exhausted; manual intervention required"}` at the top level

Then `COMPLETE`. Do not modify any other context. Do not produce code or tests.
