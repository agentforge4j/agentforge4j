## Step: Executive Summary

Produce a concise, executive-level summary of the delivery.

### Required sections

- **What was built** — 2–4 sentences describing the application as delivered
- **Key decisions** — significant architectural or scoping decisions and their rationale
- **Assumptions** — assumptions baked into the implementation that the operator should verify
- **Risks** — anything fragile, incomplete, or warranting attention
- **Next steps** — what a real engineering team should do before this is production-ready

### Output

Emit one `CREATE_FILE` command for `EXECUTIVE_SUMMARY.md` with the content as plain markdown. Then emit one `SET_CONTEXT` writing `executiveSummary` as a StringContextValue containing the same text. Then `COMPLETE`.

Do not modify any other context. Keep the summary under 600 words.
