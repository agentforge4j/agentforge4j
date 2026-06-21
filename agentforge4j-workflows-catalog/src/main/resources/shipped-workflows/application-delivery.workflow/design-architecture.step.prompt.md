## Step: Design System Architecture

Given `productVision` and `epics`, produce a coherent system architecture. The output must be detailed enough that the developer agent in the sub-workflow can implement each epic without making structural decisions.

### Required content

- **components**: each with `name`, `responsibility`, `technology`, `dependencies`
- **dataModel**: entities, key fields, relationships
- **dataFlow**: how data moves between components for each primary flow
- **boundaries**: explicit module boundaries and what crosses them
- **integrationPoints**: external systems, contracts, auth model
- **conventions**: naming, layering, error handling, validation patterns
- **technologyStack**: language, framework, persistence, build tool

### Default stack guidance

Unless the product vision dictates otherwise, default to **Java 21+ with Spring Boot 3, Maven, JPA on PostgreSQL**. State the stack explicitly.

### architectureDesign JSON shape

```json
{
  "technologyStack": {"language": "string", "framework": "string", "persistence": "string", "buildTool": "string"},
  "components": [{"name": "string", "responsibility": "string", "technology": "string", "dependencies": ["string"]}],
  "dataModel": [{"entity": "string", "fields": [{"name": "string", "type": "string"}], "relationships": ["string"]}],
  "dataFlow": [{"flow": "string", "sequence": ["string"]}],
  "boundaries": ["string"],
  "integrationPoints": [{"name": "string", "type": "string", "contract": "string"}],
  "conventions": {"naming": "string", "layering": "string", "errorHandling": "string", "validation": "string"}
}
```

### Output

Emit one `SET_CONTEXT` command writing `architectureDesign` as a JsonContextValue, then `COMPLETE`.
