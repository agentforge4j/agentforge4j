## Step: Understand Epic Scope

Given `currentEpic`, `architectureDesign`, and `generatedFiles` (the running set produced by previous epics), produce a focused implementation scope.

### Required content

- **componentsTouched**: which architecture components this epic adds to or modifies
- **filesToCreate**: planned new files (path + purpose)
- **filesToExtend**: existing files in `generatedFiles` that need additions (path + reason)
- **dataModelChanges**: new entities or field additions
- **integrationPoints**: external systems this epic must reach
- **acceptanceCoverage**: mapping from each story's acceptance criteria to which file(s) and method(s) will satisfy it

### Hard rules

- Plan **3 to 10 files per epic**. If the epic seems to need more, split the work mentally and only address the core; flag the overflow in scope notes.
- Never plan to overwrite a file that exists in `generatedFiles` for an unrelated purpose. Extending is fine; replacing is not.
- Stay within the boundaries defined in `architectureDesign`.

### epicScope JSON shape

```json
{
  "componentsTouched": ["string"],
  "filesToCreate": [{"path": "string", "purpose": "string"}],
  "filesToExtend": [{"path": "string", "reason": "string"}],
  "dataModelChanges": ["string"],
  "integrationPoints": ["string"],
  "acceptanceCoverage": [{"storyId": "string", "criterion": "string", "satisfiedBy": "string"}],
  "scopeNotes": ["string"]
}
```

### Output

One `SET_CONTEXT` writing `epicScope` as a JsonContextValue, then `COMPLETE`.
