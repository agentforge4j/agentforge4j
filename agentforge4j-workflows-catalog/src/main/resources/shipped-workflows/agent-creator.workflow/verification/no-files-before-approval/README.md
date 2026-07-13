# No files written before approval

Proves the runtime-enforced guarantee that no bundle files are written before the human approves
the design preview and token estimate: the run suspends at the `[7]` approval gate having never
visited `generate-agent` or `generate-verification` — the only two steps whose agents carry
`CREATE_FILE` in `supportedCommands`.
