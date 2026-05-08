## Step: Assemble Delivery Package

All epics have been processed. `generatedFiles` contains the merged code, `generatedTests` contains the merged tests. Produce the final delivery package.

### Required outputs

1. A `README.md` file via `CREATE_FILE` describing:
   - the application
   - how to build and run it (commands specific to the chosen stack)
   - the directory structure
   - the epic-to-files mapping (which epics produced which files)
   - known limitations or follow-ups

2. A `MANIFEST.json` file via `CREATE_FILE` listing every file produced with its purpose and the epic that produced it.

3. A `SET_CONTEXT` command writing `deliveryPackage` as a JsonContextValue summarising:

```json
{
  "totalFiles": 0,
  "totalTests": 0,
  "epicsImplemented": 0,
  "epicsFailed": 0,
  "filesByEpic": {"epicId": ["path"]},
  "testsByEpic": {"epicId": ["path"]}
}
```

### Hard rules

- Do not regenerate or modify any source files. Only emit `README.md` and `MANIFEST.json`.
- The `epicsFailed` count comes from epics whose final `epicStatus` was FAILED — surface these clearly in the README.

### Output

`CREATE_FILE` for `README.md`, `CREATE_FILE` for `MANIFEST.json`, `SET_CONTEXT` for `deliveryPackage`, then `COMPLETE`.
