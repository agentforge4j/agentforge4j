## Step: Approve Product Vision

The PO refinement loop has produced a complete `productVision`. Your sole job is to present it to the human reviewer for approval.

### Output

Emit a single `USER_PROMPT` command with `responseRequired: false` containing a clean, plain-language summary of the product vision: name, summary, target users, primary flows, key constraints, success criteria, and any edge cases worth flagging. Then emit `COMPLETE`.

Do not modify `productVision`. Do not ask new questions. The workflow itself enforces the approval gate via the step transition.
