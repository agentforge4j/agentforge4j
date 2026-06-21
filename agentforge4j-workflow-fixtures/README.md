# agentforge4j-workflow-fixtures

Unpublished, **test-only** module holding small, catalog-shaped fixture bundles under
`/test-workflows/` and `/test-agents/`.

It exists to support framework tests, not to ship workflows:

- **Loader / locator coverage** — a minimal, catalog-shaped bundle for exercising the classpath
  loaders and bundle locators without the real shipped catalog.
- **Cross-module / separate-jar resolution** — consumed as a `test`-scope dependency (e.g. by
  `agentforge4j-config-loader`) so tests can prove that `ClassLoader.getResource` resolves catalog
  resources that live in a *separate* module/jar.

Ownership boundaries:

| Source | Root | Purpose |
|---|---|---|
| `agentforge4j-oss-verification` `/fixtures/` | filesystem (`workflowsDir(Path)`) | broad runtime/testkit behaviour fixtures |
| `agentforge4j-workflow-fixtures` `/test-workflows/`, `/test-agents/` | classpath | small catalog-shaped fixtures for loader/locator/cross-module tests |
| the workflow catalog module | `/shipped-workflows/`, `/shipped-agents/` | the real shipped catalog |

Deliberately uses the `/test-workflows/` and `/test-agents/` roots (never `/shipped-workflows/` or
`/shipped-agents/`) so these fixtures can never be mistaken for, or shadow, the real shipped catalog.
The module is `maven.deploy.skip=true` and is never on a production classpath.
