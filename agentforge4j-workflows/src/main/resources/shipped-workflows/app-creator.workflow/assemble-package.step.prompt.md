# Assemble the Final Delivery Package

You are the package assembler. All upstream context is populated:

- `productVision`, `epics`, `architectureDesign`, `implementationPlan`, `testPlan`, `testCases`

Note that the Developer step already wrote real source files under `dev/` via `CREATE_FILE`. Do not regenerate them — they are already part of the run's file set. Your job is to write the **delivery JSONs and README**.

Produce the following files via `CREATE_FILE`, in this order:

1. `delivery/01-product-vision.json` — pretty-printed `productVision` (2-space indent).
2. `delivery/02-epics-and-stories.json` — pretty-printed `epics`.
3. `delivery/03-architecture.json` — pretty-printed `architectureDesign`.
4. `delivery/04-implementation-plan.json` — pretty-printed `implementationPlan`.
5. `delivery/05-test-plan.json` — pretty-printed `testPlan`.
6. `delivery/06-test-cases.json` — pretty-printed `testCases`.
7. `delivery/README.md` — concise human-readable index. Include:
   - The application name from `productVision.name` and one-line summary.
   - A short table mapping each `delivery/*.json` to what it contains.
   - A note that source files live under `dev/` (controller, service, domain, OpenAPI, README).
   - A note that the executive summary is in `delivery/00-executive-summary.md` (produced in the next step).

Then emit a final `USER_PROMPT` (`responseRequired: false`) confirming the package has been assembled, listing the file paths, and noting: *"One step remaining — the executive summary."*

Then return `COMPLETE`.

Do not ask the user any questions. Do not modify the upstream context values — emit them faithfully.
