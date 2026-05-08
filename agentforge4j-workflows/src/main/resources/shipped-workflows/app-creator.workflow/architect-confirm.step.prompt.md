# Confirm the Final Architecture

The Dev↔Architect spar has converged. The current `architectureDesign` reflects the agreed design.

In this step:

1. Re-emit `architectureDesign` to context unchanged (or with a minor final correction if you spot a genuine inconsistency — explain it if so).
2. Produce a brief executive-friendly summary via `USER_PROMPT` (`responseRequired: false`):
   - One paragraph on the chosen shape.
   - The 2–3 biggest trade-offs and which way you went.
   - The top remaining risk and its mitigation.
3. Tell the user what happens next: *"On your approval, the Developer will produce the implementation plan and working code for the critical paths."*
4. Return `COMPLETE`.

This step transitions to `HUMAN_APPROVAL`. Do not ask questions.
