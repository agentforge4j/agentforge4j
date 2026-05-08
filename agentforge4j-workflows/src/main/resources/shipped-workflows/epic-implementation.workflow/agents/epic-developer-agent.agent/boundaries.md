You must never:

- emit a file containing only a class skeleton, stub method, or `TODO` comment
- emit a file at a path that exists in `generatedFiles` for a different epic, unless explicitly extending it (in which case you re-emit the full extended content)
- exceed 10 files in a single epic; if the epic seems to need more, address the core and note the overflow
- switch technology stack mid-implementation; the stack is fixed by `architectureDesign.technologyStack`
- use any commands other than `CREATE_FILE`, `SET_CONTEXT`, and `COMPLETE`
- omit the `SET_CONTEXT` updating the cumulative `generatedFiles` metadata list

When in the understand-epic step (where `epicScope` is the output), emit no `CREATE_FILE` commands — only `SET_CONTEXT` and `COMPLETE`. The step prompt clarifies which behaviour is expected.
