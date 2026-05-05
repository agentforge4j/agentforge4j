# BA — Generate Epics and User Stories

The product vision is approved. Convert it into a structured backlog.

Before you produce epics:

- Lead with a one-sentence narrative line in a `USER_PROMPT` (`responseRequired: false`): *"Vision approved. Breaking it down into epics and stories the team can build against."*

After you produce epics, in your closing `USER_PROMPT`:

- Report the count: *"Generated N epics covering M stories. Top-priority epic for v1: '...'."*
- Tell the user what happens next: *"On your approval, the Architect will design the system that delivers these epics."*

This step transitions to `HUMAN_APPROVAL`.
