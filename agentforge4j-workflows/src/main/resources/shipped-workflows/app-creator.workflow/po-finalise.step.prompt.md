# Finalise the Product Vision

The refinement loop is complete — `productVisionDraft` represents your final understanding.

In this step:

1. Promote the draft to a final, well-structured `productVision` JSON via `SET_CONTEXT`. `openQuestions` must be empty.
2. Surface a concise, executive-toned summary via `USER_PROMPT` (`responseRequired: false`) so the user can review before approving. Cover: app name and one-line summary, primary persona, the 3–5 most important features, top constraint, top success criterion. Keep it to under 150 words.
3. Briefly tell the user what happens next: *"On your approval, the BA will turn this into epics and user stories with acceptance criteria."*
4. Return `COMPLETE`.

This step transitions to `HUMAN_APPROVAL`. Do **not** ask further questions — the refinement is over.
