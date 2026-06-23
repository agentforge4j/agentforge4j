# Resource Loading

## What this teaches

That not every workflow step calls the LLM. A `RESOURCE` step is a deterministic, non-AI step that
loads a bundled resource and materialises it into the workflow context for later steps to use. No
Spring, no real LLM keys, no network — and in this example, no agent at all.

## AgentForge4j capability demonstrated

`ResourceBehaviour`. The `RESOURCE` step reads `resourcePath` (a classpath resource under an
allow-listed root such as `/workflow-resources/`), loads its UTF-8 text, and stores it in the
context under `contextKey`; the run then completes. Because no agent runs, no model output is
scripted — the runtime still requires an LLM resolver, so an empty fake script is wired and never
consulted.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-resource -am verify
```

`verify` runs the deterministic test, which asserts the run completes and the context holds the
loaded text. To watch it print, run `WlResourceExample.main` from your IDE.

## Expected behaviour / output

`main` runs the workflow and prints, for example:

```text
status=COMPLETED
welcome=StringContextValue[value=Welcome to AgentForge4j. ..., provenance=...]
```

The bundled test asserts the terminal `COMPLETED` state and the loaded context value deterministically.

## Files to read first

1. `src/main/resources/workflows/wl-resource.workflow/workflow.json` — the single `RESOURCE` step,
   its `resourcePath` and `contextKey`.
2. `src/main/resources/workflow-resources/welcome.txt` — the bundled resource that gets loaded.
3. `src/main/java/.../WlResourceExample.java` — assembles the runtime (no agents) and reads the
   loaded value back out of the context.
4. `src/test/java/.../WlResourceExampleTest.java` — the deterministic assertion.
