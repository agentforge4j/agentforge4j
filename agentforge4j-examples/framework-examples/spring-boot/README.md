# Spring Boot

## What this teaches

How to adopt AgentForge4j in a Spring Boot application using the starter: auto-configuration wires
the runtime so you inject an `AgentForge4j` bean and run a workflow with no manual assembly. No real
LLM keys, no network.

## AgentForge4j capability demonstrated

The `agentforge4j-spring-boot-starter` auto-configures the `AgentForge4j` facade from
`application.properties`. The example adds the two beans a deterministic, offline run needs: a
run-agnostic `StaticFakeResponseSource` (overriding the starter's run-scoped default, which is
`@ConditionalOnMissingBean`) and the workflows/agents paths resolved from the module's classpath. A
`CommandLineRunner` starts the workflow on boot.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl framework-examples/spring-boot -am verify
```

`verify` runs the `@SpringBootTest`, which boots the full context and asserts the workflow reaches
`COMPLETED`. To run the app and see it print, use:

```bash
./mvnw -pl framework-examples/spring-boot spring-boot:run
```

## Expected behaviour / output

The application starts, the `CommandLineRunner` runs the workflow to `WorkflowStatus.COMPLETED`, and
prints, for example:

```text
Workflow 'spring-boot-demo' (run <id>) finished with status: COMPLETED
```

The bundled test asserts the terminal status deterministically.

## Files to read first

1. `src/main/java/.../SpringBootExampleApplication.java` — the `@SpringBootApplication`, the
   `FakeResponseSource` bean, the config-path wiring, and the `CommandLineRunner`.
2. `src/main/resources/application.properties` — enables the fake provider and disables shipped
   defaults.
3. `src/main/resources/workflows/spring-boot-demo.workflow/workflow.json` — the one-step workflow.
4. `src/test/java/.../SpringBootExampleApplicationTest.java` — the deterministic `@SpringBootTest`.
