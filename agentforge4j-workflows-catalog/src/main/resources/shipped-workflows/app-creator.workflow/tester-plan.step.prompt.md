# Tester — Test Strategy and Cases

The implementation plan is in place. You now produce a test strategy and a comprehensive set of test cases mapped to acceptance criteria, including real architecture-specific failure modes.

Before you start, lead with a one-sentence narrative line in a `USER_PROMPT` (`responseRequired: false`):

*"Designing the test strategy and cases. Every acceptance criterion will be traceable to specific tests, with real failure-mode and edge coverage."*

Reminders for this step (also in your system prompt):

- Build a **traceability matrix** so every story-AC pair is linked to covering test case ids.
- At least **35%** of test cases must be `negative`, `edge`, or `failure-mode`.
- Each non-functional concern in `architectureDesign` (security, scalability, observability, data residency) must have at least one test.
- Pull architecture-specific failure modes from `dataFlow[].failureModes` and `integrations[].failureHandling` and convert each to a test case.

In your closing `USER_PROMPT`:

- Report counts: total cases, by level, by kind (happy/edge/negative/failure-mode/non-functional), and any uncovered ACs with reasons.
- Tell the user what happens next: *"The full delivery package will now be assembled, followed by an executive summary."*

This step is `AUTO`.
