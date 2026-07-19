# AgentForge4j

[![CI](https://github.com/agentforge4j/agentforge4j/actions/workflows/ci.yml/badge.svg)](https://github.com/agentforge4j/agentforge4j/actions/workflows/ci.yml)
[![CodeQL](https://github.com/agentforge4j/agentforge4j/actions/workflows/codeql.yml/badge.svg)](https://github.com/agentforge4j/agentforge4j/actions/workflows/codeql.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=agentforge4j&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=agentforge4j)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)

> **Pre-1.0.** AgentForge4j is preparing for its first public open-source release. APIs and workflow contracts may still change before 1.0. It is not yet published to Maven Central and should not yet be treated as production-certified. See [Project status](#project-status).

---

## In 30 seconds

**AgentForge4j is the embeddable Java framework for governed AI workflows.**

LLMs are non-deterministic. Enterprise execution cannot be. AgentForge4j puts controlled workflow structure around AI-assisted work: explicit steps, inspectable run state, retries, human approval gates, tool execution, provider-independent models, testable workflows, and a decision-level audit trail.

You define *what* runs and *in what order*. The runtime executes that definition faithfully — it never improvises which agent runs next.

---

## Why AgentForge4j exists

Most AI frameworks make it easy to *call* a model. Far fewer make AI-driven work **predictable, auditable, resumable, testable, and safe to operate** inside a larger organization.

That gap is the point of this project. When an LLM is part of a real business process, teams need to answer questions that a raw model call cannot:

- Which step made this decision, and on what input?
- Can a human approve or reject before anything irreversible happens?
- If a run fails or blocks halfway, can it resume instead of starting over?
- Can the same workflow be tested deterministically before it reaches production?
- Can we switch model providers without rewriting the workflow?

AgentForge4j is built around those questions. The workflow author decides the structure up front; the runtime gives you the state, retries, approvals, events, and provider abstraction needed to run that structure with confidence.

### The story behind the project

AgentForge4j started as a personal training project — a way to learn more about AI in Java. Early
on it was mostly for fun: build something, experiment, and see how far Java could go in the
AI-agent space.

Over time the experiments kept running into the same enterprise gap described above — and closing
that gap became the project's purpose.

During the early experimental phase, I also hit a hard but useful learning moment: secrets were committed while the project was still moving fast. I treated that as a reset point instead of patching around it. The repository was recreated, the architecture was cleaned up, and AgentForge4j was rebuilt more intentionally as a clean Apache-2.0 open-source framework.

---

## What AgentForge4j provides

- **Explicit workflow steps** — workflows are defined in external configuration (JSON + markdown), not hardcoded in Java. No agents or steps are selected dynamically at runtime.
- **Inspectable run state** — every run carries an execution state (`WorkflowState`): status, shared context, pending gates, and failure details. In-memory by default; durable persistence is opt-in.
- **Retries** — per-step retry behaviour for transient failures and rework loops, plus opt-in LLM call retry with backoff.
- **Human input, review, and approval** — first-class human gates that pause a run, capture a decision, and continue.
- **Structured tool execution** — tools are invoked through a typed command and a `ToolProvider` SPI, not by parsing free text.
- **Model / provider abstraction** — a `ModelTier` concept plus a `ServiceLoader`-discovered provider model lets workflows stay independent of any one vendor.
- **Workflow testing** — a fake LLM client and a dedicated test kit (`agentforge4j-testkit`) make workflow runs deterministic and repeatable in tests.
- **Decision-level audit events** — a structured event log records what happened at each step, including LLM calls, retries, and approvals.

---

## What AgentForge4j is *not*

AgentForge4j is deliberately narrow. It does not try to replace these excellent tools — it complements them by focusing on **structured execution around AI work**.

| Tool | What it is great at | How AgentForge4j differs |
|---|---|---|
| **LangChain4j** | Java LLM integration patterns, chains, RAG building blocks | AgentForge4j focuses on workflow *execution* — state, approvals, retries, and auditability around AI work. You can use LangChain4j-style integrations beneath it. |
| **Spring AI** | Bringing AI capabilities into Spring applications | AgentForge4j is a workflow runtime usable *from* Spring (via a starter) but not *only* a Spring abstraction — its core is framework-neutral. |
| **Camunda** | Full BPM / business-process automation | AgentForge4j is lighter and AI-agent-focused, not a general BPMN engine. |
| **Temporal** | General-purpose durable execution | AgentForge4j is domain-specific for AI workflows and decision-level auditability, not a generic durable-execution platform. |
| **Autonomous agent frameworks** | Open-ended, self-directed agent loops | AgentForge4j is the opposite by design: structured, reviewable, repeatable AI-assisted workflows — not uncontrolled agent autonomy. |

If you need a general workflow engine, durable-execution platform, or autonomous agent swarm, use the tools above. If you need **controlled structure around AI-assisted decisions in Java**, that is what AgentForge4j is for.

---

## Core concepts

These are the live terms used throughout the codebase.

| Concept | Type | What it is |
|---|---|---|
| **Workflow** | `WorkflowDefinition` | The ordered definition of steps, blueprints, and input artifacts for a process. Immutable and validated. |
| **Agent** | `AgentDefinition` | A reusable agent: system prompt, locality, provider preferences, and model tier. Defined in external config, never hardcoded. |
| **Step** | `StepDefinition` | A single executable unit of a workflow — behaviour, prompt, context mapping, and optional model-tier override. |
| **Run** | `WorkflowState` | The runtime state of one execution: run id, status, shared context, pending gates, and failure details. |
| **Command** | `LlmCommand` | The structured output an agent returns. The runtime dispatches on command type — free text is never interpreted. |
| **Human gate** | `ESCALATE` / `StepApprovalDecision` | A point where the run pauses for human input, review, or approval before continuing. |
| **Tool call** | `ToolProvider` + `TOOL_INVOCATION` | Typed, explicit invocation of an external tool through an SPI. |
| **Event log** | `WorkflowEvent` / `WorkflowEventLog` | The decision-level audit trail of everything that happened during a run. |
| **Model tier** | `ModelTier` | A provider-independent capability level (`LITE`, `STANDARD`, `POWERFUL`) so workflows pick capability, not a vendor model name. |

Agents return a typed list of commands rather than prose. Current command types include
`CREATE_FILE`, `SET_CONTEXT`, `USER_PROMPT`, `RUN_COMMAND`, `TOOL_INVOCATION`,
`GENERATE_QUESTIONS`, `ESCALATE`, `CONTINUE`, and `COMPLETE`. The runtime turns each command
into a controlled side effect.

---

## Quickstart

> Not yet on Maven Central. For now, build from source and use the SNAPSHOT locally.

### Prerequisites

- Java 17
- Maven 3.9+ (a wrapper, `./mvnw`, is included)

### Build from source

```bash
git clone https://github.com/agentforge4j/agentforge4j.git
cd agentforge4j
./mvnw clean install
```

### Run a workflow without Spring

The `agentforge4j-bootstrap` module assembles a fully wired runtime in plain Java — no container
required. Agent and workflow definitions are external JSON + markdown; point bootstrap at the
directories that hold yours, or run one of the shipped workflows described below (see
[Included workflows](#included-workflows)).

```java
import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import java.nio.file.Path;

AgentForge4j af = AgentForge4jBootstrap.defaults()
    .withAgentsDir(Path.of("agents"))
    .withWorkflowsDir(Path.of("workflows"))
    .withLlmProvider(LlmProviderConfig.openai()
        .defaults()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .build())
    .build();

String runId = af.start("your-workflow-id");
```

Add the LLM provider modules you want on the classpath (for example `agentforge4j-llm-openai`,
`agentforge4j-llm-ollama`); providers are discovered via `ServiceLoader<LlmClientFactory>`.
See [`agentforge4j-bootstrap/README.md`](agentforge4j-bootstrap/README.md) for the full
configuration surface (file sinks, retries, persistence overrides, environment variables). For a
complete, runnable, offline version of this snippet — including the agent and workflow definition
files it loads — see
[`agentforge4j-examples/framework-examples/quick-start`](agentforge4j-examples/framework-examples/quick-start).

### Use it from Spring Boot

Depend on `agentforge4j-spring-boot-starter`. Its `BootstrapAutoConfiguration` delegates to
bootstrap and exposes a single `AgentForge4j` bean you can inject. The Maven coordinates
(once published) are:

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-spring-boot-starter</artifactId>
  <version>${agentforge4j.version}</version>
</dependency>
```

---

## Included workflows

The shipped workflow catalog (`agentforge4j-workflows-catalog`, loaded automatically by bootstrap)
carries the 0.1.0 launch line-up, rebuilt from scratch against the current workflow language and
quality bar:

- **Workflow Execution Estimator** — analyzes a workflow definition and computes a full estimate
  (shape, turn counts, and a token-range envelope with a recommendation and confidence) before it
  runs, disclosing the complete estimate in-workflow ahead of a human approval pause.
- **Agent Creator** — turns a freeform agent idea into a downloadable, deterministically-validated
  agent bundle (definition, system prompt, and behavioural boundaries) behind a human approval gate.

The fuller catalog (workflow generator, application delivery, and more) is still being designed —
see the [Roadmap](#roadmap). You can also author your own workflow definitions (plain JSON +
markdown — see the [Quickstart](#quickstart)) or start from the runnable definitions in
[`agentforge4j-examples`](agentforge4j-examples).

---

## Architecture

AgentForge4j is intentionally split into focused modules so the runtime stays clean, embeddable,
and framework-neutral. The project is licensed under **Apache-2.0**.

Dependencies flow strictly one way, with no cycles: the core domain model depends only on the
shared validation utility; the runtime composes the domain model, config loading, and the LLM
abstraction; `agentforge4j-bootstrap` assembles the runtime; and the Spring Boot starter sits on
top of bootstrap. Nothing below the starter knows Spring exists.

| Layer | Modules | Role |
|---|---|---|
| **Core / runtime** | `agentforge4j-core`, `agentforge4j-runtime`, `agentforge4j-config-loader`, `agentforge4j-schema` | Framework-neutral workflow execution: domain model, state, commands, events. No Spring, no database. |
| **LLM providers** | `agentforge4j-llm-api`, `agentforge4j-llm`, and provider modules (`-openai`, `-claude`, `-gemini`, `-mistral`, `-ollama`, `-vllm`, `-bedrock`, `-azure-openai`, `-openai-compatible`) | Model/provider integrations behind a common abstraction, discovered via `ServiceLoader`. |
| **Tooling** | `agentforge4j-tools-http`, `agentforge4j-mcp` | Tool execution, including HTTP tools and Model Context Protocol support. |
| **Bootstrap** | [`agentforge4j-bootstrap`](agentforge4j-bootstrap/README.md) | Programmatic assembly of the full runtime without requiring Spring. |
| **Spring integration** | `agentforge4j-spring-boot-starter` | Auto-configuration for Spring Boot applications. |
| **Testing** | `agentforge4j-llm-fake`, `agentforge4j-testkit` | A fake LLM client plus a dedicated test kit (harness, run assertions, scripted gate responses) for deterministic, repeatable workflow tests. |
| **Workflow catalog** | `agentforge4j-workflows-catalog` | The shipped workflow catalog (see [Included workflows](#included-workflows)). |

> The visual Workflow Builder is published separately as version **v0.6.1**. Its public documentation describes the currently released capabilities.

Most framework-neutral modules carry a JPMS `module-info.java`. The Spring Boot starter is consumed on the classpath by design, and MCP currently depends on SDK module-name constraints.

---

## Why enterprises may care

- **Deterministic structure around non-deterministic output** — the workflow shape is fixed and reviewable; only the content inside steps is AI-generated.
- **Human approvals where they matter** — gates pause execution for review before anything irreversible happens.
- **An audit trail for decisions** — a structured event log captures what each step did, including model and tool calls.
- **Resumable runs** — failed or blocked runs carry execution state and can continue rather than restart.
- **Provider independence** — model tiers and a pluggable provider model avoid lock-in to a single vendor.
- **Safer tool execution** — tools run through typed commands and an SPI, not free-text parsing.
- **Testable before production** — fake-LLM-driven tests make workflows repeatable and verifiable.

---

## Roadmap

### Toward OSS 1.0

- Harden runtime APIs and workflow contracts
- Expand the shipped workflow catalog
- Add complete example applications
- Improve workflow testing and verification
- Improve documentation, diagrams, and module READMEs
- Strengthen tool execution safety and policy controls
- Improve observability around runs, retries, approvals, model calls, and tool calls

### Planned workflow / catalog expansion

- Workflow Creator
- Documentation generator
- Architecture review workflow
- Code review workflow
- Release readiness workflow
- README / PlantUML review workflows

### Future OSS features

- **Context preloading / knowledge packs** — cache selected documentation (Confluence pages, markdown, ADRs, API specs, architecture docs) so workflows can draw on stable project knowledge during execution.
- **Codebase input support** — let workflows inspect uploaded repository snapshots or selected source files, with strict file boundaries, auditability, and human approval before any generated change is applied.
- **Deeper model tiering** — add a top-end tier for deep-reasoning / frontier models (the final tier name is not locked yet).
- **Run debugger** — a clearer run timeline showing context changes, LLM outputs, tool calls, retries, approvals, and blocking points.
- **Policy-driven execution** — explicit policies for tool use, approval requirements, model-tier limits, and safe execution boundaries.

### After OSS 1.0: AgentForge4j Platform

A future self-hosted layer for teams that need workflow libraries, run dashboards, access 
control, tenant-aware administration, metering, operational governance, and a fuller UI around 
AgentForge4j workflows. Planned capabilities also include AI FinOps and cost attribution, helping 
enterprises understand provider spend across tenants, teams, workflows, runs, steps, and business 
outcomes. The OSS framework will remain clean and independent; the Platform will build on top 
of it rather than replacing it.

---

## Examples

Runnable examples live in [`agentforge4j-examples`](agentforge4j-examples) (a standalone Maven
tree, deliberately outside the framework reactor so each example builds the way an embedder
would):

- **Framework examples** — `quick-start` (the shortest runnable program, offline via the fake LLM
  client), `spring-boot` (starter integration), `human-approval` (a human gate pausing and
  resuming a run), `tools-http` (governed HTTP tool execution), and `mcp` (Model Context Protocol
  tools).
- **Workflow-language examples** — one small workflow per language feature: branching, loops,
  retries, SPAR, human-in-the-loop, and resources.

Planned additions as the catalog and demos mature: generated frontend / backend / documentation
workflows and an AI Agent Adoption demo application.

---

## Project status

- **Version:** `0.1.0-SNAPSHOT` — release candidate for the first public OSS release.
- **Not yet on Maven Central.** Build from source until the 0.1.0 artifacts are published.
- **APIs and workflow contracts may change** before 1.0.
- The core, runtime, bootstrap, provider modules, test kit, workflow catalog, runnable examples,
  MCP integration, and published Workflow Builder v0.6.1 are in place.

Treat it as a capable pre-1.0 framework that is approaching public release, not yet as a frozen or production-certified platform.

---

## Learn more & community

- **GitHub Issues** — bug reports and feature requests: <https://github.com/agentforge4j/agentforge4j/issues>
- **GitHub Discussions** — questions and ideas: <https://github.com/agentforge4j/agentforge4j/discussions>
- **Contributing** — see [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **Security** — see [SECURITY.md](SECURITY.md)

---

## Maintainer

AgentForge4j is created and maintained by **André Groeneveld**.

It is built as a credible, community-friendly open-source project — contributions, issues, and
discussion are welcome.

---

## License

[Apache 2.0](LICENSE)
