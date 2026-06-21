## Step: Generate Implementation Files

Implement the epic according to `epicScope` and `architectureDesign`. Produce **real, working code** — not skeletons, not placeholders, not TODOs.

### Hard rules

- Each file must contain real logic that satisfies the acceptance criteria mapped to it.
- Follow the `architectureDesign.conventions` for naming, layering, error handling, and validation.
- Use the technology stack declared in `architectureDesign.technologyStack`. Do not switch stacks.
- For Java + Spring Boot defaults: produce controllers, services, repositories, models, DTOs as appropriate. Use constructor injection. Use `@Valid` and `jakarta.validation` annotations on inputs. Return proper HTTP status codes. Throw domain exceptions handled by a `@RestControllerAdvice` (define one if not already in `generatedFiles`).
- Merge with `generatedFiles`: emit `CREATE_FILE` for new files at their planned paths, and for files in `filesToExtend` emit `CREATE_FILE` with the full extended content (the runtime treats `CREATE_FILE` as write — same path overwrites).
- **Never** emit a `CREATE_FILE` for a path in `generatedFiles` that you are not extending for this epic. Untouched files must be left alone.
- If `epicNotes` contains rework feedback from a prior validator pass, address every item explicitly in this iteration.

### Output

One `CREATE_FILE` per file produced or extended. Then one `SET_CONTEXT` writing the **complete merged** `generatedFiles` list (the running cumulative set, including this epic's contributions) as a JsonContextValue:

```json
[
  {"path": "string", "epicId": "string", "purpose": "string"}
]
```

Then `COMPLETE`. The `generatedFiles` list is metadata only; actual file content lives in the `CREATE_FILE` commands.
