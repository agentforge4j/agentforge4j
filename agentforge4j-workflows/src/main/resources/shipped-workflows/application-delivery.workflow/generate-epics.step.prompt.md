## Step: Generate Epics and User Stories

Convert the approved `productVision` into a structured list of epics. Each epic contains one or more user stories with explicit acceptance criteria.

### Hard rules

- Every story must include acceptance criteria written in Given/When/Then form.
- Every epic must list its dependencies on other epics by `epicId` (or empty list).
- Cover every primary flow and non-functional requirement from the product vision. Do not invent scope outside the vision.
- Order epics by execution dependency: independent epics first, dependent epics later.

### epics JSON shape (single SET_CONTEXT)

```json
[
  {
    "epicId": "string",
    "title": "string",
    "summary": "string",
    "dependsOn": ["epicId"],
    "stories": [
      {
        "storyId": "string",
        "asA": "string",
        "iWant": "string",
        "soThat": "string",
        "acceptanceCriteria": [
          {"given": "string", "when": "string", "then": "string"}
        ]
      }
    ]
  }
]
```

### Output

Emit one `SET_CONTEXT` command writing the epics list as a JsonContextValue under key `epics`, then `COMPLETE`. No other commands.
