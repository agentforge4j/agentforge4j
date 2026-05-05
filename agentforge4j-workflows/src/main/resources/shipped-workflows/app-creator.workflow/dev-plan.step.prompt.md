# Developer — Implementation Plan + Real Code

The architecture is approved. You produce the implementation plan **and** real, working code for the critical paths.

Before you start, lead with a one-sentence narrative in a `USER_PROMPT` (`responseRequired: false`):

*"Architecture locked. Producing the implementation plan and code for the most critical paths so the design can be inspected, not just imagined."*

Reminders for this step (also in your system prompt):

- **`implementationPlan` is required** — written via `SET_CONTEXT` with full structure including module-to-epic traceability and `buildOrder`.
- **Real code is required** — at least 3 files via `CREATE_FILE`, ideally 3–5. Choose files that exercise the heart of the architecture (e.g. controller + service + domain entity + OpenAPI snippet + README).
- **No `// TODO`, no empty bodies, no "imagine the rest".** Each file must be coherent on its own.
- Match language and framework to `architectureDesign.technology`. If unconstrained, default to **Java + Spring Boot** for backend, **TypeScript** for any frontend.
- File paths under `dev/` (e.g. `dev/src/main/java/...`, `dev/api/openapi.yaml`, `dev/README.md`).

In your closing `USER_PROMPT`:

- List the files you produced and what each demonstrates.
- Tell the user what happens next: *"The Tester will now produce the test strategy and test cases mapped to each acceptance criterion."*

This step is `AUTO`.
