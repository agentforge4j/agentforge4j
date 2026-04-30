# AgentForge4j <WIP>

**NOTE:** This project is in active development. The README will evolve as the project progresses. The current content is a comprehensive overview of the vision, architecture, and roadmap, but some details may change as the implementation unfolds.

**Structured multi-agent AI workflow orchestration for the Java ecosystem.**

AgentForge4j is an open-source Java framework for designing and running predictable, auditable, human-in-the-loop AI workflows. It is inspired by workflow engines like Camunda, but purpose-built for AI agent orchestration. Workflows are fully defined in configuration — no hardcoded agents, no runtime surprises, no black boxes.

> *"Agents of AI, forged in Java, for everyone."*

The name comes from two places. The *Agent* part is a nod to the idea of agents with a mission — think Agents of S.H.I.E.L.D., but for AI. The *Forge* part is a nod to the idea of crafting something powerful and intelligent from raw materials — think Tony Stark in the workshop, but the thing being forged is your workflow. *4j* means it is written in Java, for the world — not Java for Java developers only, but a platform that does not discriminate.

> **Status:** Active development. Core modules are being built and reviewed. See the [Roadmap](#roadmap) for what is available now and what is coming.

---

## What Makes It Different

Most AI agent frameworks make runtime decisions about which agent to use, making workflows unpredictable and hard to audit. AgentForge4j takes the opposite approach.

**Workflows must be predictable.** The workflow author decides which agent runs at each step when designing the workflow. The runtime executes that decision faithfully — it never selects agents dynamically. Every workflow run is auditable, repeatable, and fully defined by its configuration.

**Everything is external configuration.** No agents, no workflows, and no steps are hardcoded in Java. Agent definitions, system prompts, and workflow definitions live in external JSON and markdown files. You can change a workflow without touching Java code. A planned web UI will let you design workflows visually without touching any files at all.

**The framework does not discriminate.** AgentForge4j is designed for any structured multi-agent process — not just software development. Team building exercises, competition design, educational workflows, business process automation. If it can be modelled as a sequence of steps with agents, it fits.

---

## Use Cases

**Software generation** — an idea becomes running software through a sequence of PO, BA, architect, developer, tester, and security agents, each doing their part in a defined workflow. Each agent produces structured output that the next agent builds on, with human approval gates at the steps that matter.

**Teaching and lesson planning** — a teacher describes a topic, a year group, and a time period. A curriculum agent structures the semester into units, a lesson planning agent drafts each lesson plan with objectives and activities, a differentiation agent adapts the material for different learning needs, and a resource agent suggests supporting materials. The teacher reviews and approves at each stage, keeping full control over the final plan. What would take days of preparation is reduced to a structured, human-guided workflow.

**Professional kitchen and recipe development** — a head chef describes a new dish concept, available seasonal ingredients, and the restaurant's cuisine style. A flavour profile agent explores ingredient combinations, a technique agent proposes preparation and cooking methods, a plating agent suggests presentation ideas, and a costing agent estimates the dish cost against the target margin. The chef makes the creative decisions at every human approval gate — the workflow handles the research and structure, not the craft.

**Embedding core and runtime in your own application** — if you are a developer who wants AI workflow orchestration without the full stack, you embed `agentforge4j-core` and `agentforge4j-runtime` directly. You bring your own persistence by implementing `WorkflowStateRepository`. You bring your own LLM provider by implementing `LlmClientFactory` and registering it via `ServiceLoader`. The framework handles execution, state, retries, human approval gates, and audit events. You own the application layer. For developers who prefer a managed setup, Spring Boot and Quarkus starters are planned that wire everything together automatically.

**Self-referential demo** — the long-term goal once AgentForge4j is feature-complete is to use AgentForge4j itself to generate its own CLI and web UI. A framework that can orchestrate its own creation is the strongest possible demonstration of what it can do.

---

## Architecture

### Module Structure

All modules live in a single Maven monorepo, versioned and released together. Java 26, JPMS `module-info.java` throughout.

| Module | Description | Status   |
|---|---|----------|
| `agentforge4j-util` | Shared validation utility. No dependencies beyond JDK and commons-lang3. | Complete |
| `agentforge4j-core` | Pure Java domain model. No Spring, no IO, no database. | Planned  |
| `agentforge4j-llm` | Shared LLM abstractions. No workflow knowledge. | Complete |
| `agentforge4j-llm-openai` | OpenAI provider using the Responses API. | Planned  |
| `agentforge4j-llm-ollama` | Ollama provider for local model execution. | Planned  |
| `agentforge4j-llm-claude` | Anthropic Claude provider. | Planned  |
| `agentforge4j-llm-vllm` | vLLM provider. | Planned  |
| `agentforge4j-config-loader` | Loads agent and workflow definitions from the filesystem. | Planned  |
| `agentforge4j-runtime` | Workflow execution state and command model. | Planned  |
| `agentforge4j-persistence-jdbc` | Optional JDBC persistence for workflow state. | Planned  |
| `agentforge4j-persistence-jpa` | Optional JPA persistence for workflow state. | Planned  |
| `agentforge4j-api` | Spring Boot thin facade over the runtime. | Planned  |
| `agentforge4j-spring-boot-starter` | Auto-configuration for Spring Boot applications. | Planned  |
| `agentforge4j-quarkus-extension` | Quarkus extension for auto-configuration. | Planned  |
| `agentforge4j-cli` | Thin CLI facade over the runtime. | Planned  |
| `agentforge4j-web-ui` | Web-based workflow designer and runtime dashboard. | Planned  |

### Dependency Direction

Dependencies flow strictly one way. No module may depend on a module below it in the chain.

```
util
 ├── core
 ├── llm
 │    ├── llm-openai
 │    ├── llm-ollama
 │    ├── llm-claude
 │    └── llm-vllm
 └── config-loader (depends on core + util)
      └── runtime (depends on core + config-loader + llm + util)
           └── api
                └── starters
```

`core` never depends on `config-loader`. `config-loader` never depends on `runtime`. `llm` has no workflow knowledge.

### Key Design Decisions

**Plugin model for LLM providers.** Providers are discovered via Java `ServiceLoader` and JPMS modules. Adding a new provider is a matter of implementing two interfaces and registering the module — no changes to the framework core.

**LOCAL vs CLOUD agent locality.** Every agent is marked as either `LOCAL` (runs on infrastructure you control, such as Ollama) or `CLOUD` (calls an external API, such as OpenAI or Anthropic). Privacy-conscious deployments can restrict workflows to LOCAL agents only.

**Structured LLM output.** Agents return structured JSON arrays of typed commands — `CREATE_FILE`, `SET_CONTEXT`, `USER_PROMPT`, `RUN_COMMAND`, `COMPLETE`, `GENERATE_QUESTIONS`, `ESCALATE`. The runtime dispatches on command type to produce real-world side effects. Free text from the LLM is never parsed or interpreted.

**In-memory by default, persistence opt-in.** The runtime starts with in-memory state. JDBC and JPA persistence modules are opt-in. Developers embedding the core bring their own persistence and are never forced to use a database.

---

## Filesystem Layout

Agent and workflow definitions live outside the Java codebase entirely.

```
agents/
  my-agent.agent/
    agent.json                  # agent definition (id, name, locality, provider preferences)
    systemprompt.md             # system prompt
    boundaries.md               # optional behavioural boundaries, appended to system prompt

workflows/
  my-workflow.workflow/
    workflow.json               # workflow definition and step sequence
    my-blueprint.blueprint.json # optional reusable step sequences
    my-artifact.artifact.json   # optional input form definitions
```

---

## Getting Started

> The framework is not yet available on Maven Central. Once the first modules are published, installation instructions will appear here.

### Prerequisites

- Java 26
- Maven 3.9 or later

### Build from Source

```bash
git clone https://github.com/agentforge4j/agentforge4j.git
cd agentforge4j
mvn clean install
```

---

## Roadmap

The project is built module by module in a defined order. Each module is fully reviewed before the next one begins.

**Foundation**
- [X] `agentforge4j-util` — validation utility
- [ ] `agentforge4j-core` — domain model
- [X] `agentforge4j-llm` — LLM abstractions
- [ ] `agentforge4j-llm-openai` — OpenAI provider
- [ ] `agentforge4j-llm-ollama` — Ollama provider
- [ ] `agentforge4j-llm-claude` — Anthropic Claude provider
- [ ] `agentforge4j-llm-vllm` — vLLM provider
- [ ] `agentforge4j-config-loader` — filesystem config loading

**Execution**
- [ ] `agentforge4j-runtime` — workflow execution engine and command model

**API and Integration**
- [ ] `agentforge4j-api` — Spring Boot thin facade over the runtime
- [ ] `agentforge4j-spring-boot-starter` — auto-configuration for Spring Boot
- [ ] `agentforge4j-quarkus-extension` — Quarkus extension for auto-configuration

**Persistence**
- [ ] `agentforge4j-persistence-jdbc` — optional JDBC persistence
- [ ] `agentforge4j-persistence-jpa` — optional JPA persistence

**Tooling and Demo**
- [ ] Self-referential demo — AgentForge4j generating its own CLI and web UI using AgentForge4j workflows
- [ ] `agentforge4j-cli` — thin CLI facade over the runtime created by AgentForge4j itself in the self-referential demo
- [ ] `agentforge4j-web-ui` — web-based workflow designer with a drag-and-drop canvas for building workflows visually, plus a *workflow creator* workflow that asks you a few questions and generates the workflow configuration for you
- [ ] Docker image for `agentforge4j-api` — once the API is complete, an official image will be published for running the runtime and API without building from source
- [ ] Docker image for `agentforge4j-web-ui` — a standalone image for the web UI, ready to run against your own API instance

---

## AI Development Agents

AgentForge4j is written by human developers. These agents exist to handle the repetitive, mechanical work — generating boilerplate unit tests, filling in missing Javadoc, checking that code follows project conventions — so developers can stay focused on the design and logic that actually requires human thinking. Every agent output is reviewed and owned by the developer who commits it.

GitHub Copilot agent prompts live in `.github/agents/`. They defer to `.github/copilot-instructions.md` as the single source of truth for project conventions — update that file when the project evolves and the agents stay current automatically.

### The Developer Loop

```
Write code
    ↓
@review-agent        — catch standard violations before they are committed
    ↓
@javadoc-agent       — document the public API
    ↓
@unit-test-agent     — generate test first drafts
    ↓
@commit-agent        — stage and commit with a well-formed message
    ↓
git push             — always done by the developer, never by an agent
```

### How to Use in IntelliJ with GitHub Copilot

1. Open the **GitHub Copilot Chat** panel in IntelliJ
2. Attach the file or files you want to work on
3. Reference the agent by name in your prompt

`.github/copilot-instructions.md` is loaded automatically — you do not need to attach it manually.

**Example prompts:**

> Review the attached files for standard violations following @review-agent

> Add missing Javadoc to the attached module following @javadoc-agent

> Generate unit tests for the attached file following @unit-test-agent

> Analyse my changes and prepare a commit following @commit-agent

### Available Agents

| Agent | Purpose |
|---|---|
| `review-agent.agent.md` | Flags standard violations in new or modified code. Reports only — never auto-fixes. |
| `javadoc-agent.agent.md` | Adds missing Javadoc to exported public API. Scopes itself via `module-info.java`. |
| `unit-test-agent.agent.md` | Generates JUnit 5 test first drafts. Output must be reviewed before committing. |
| `commit-agent.agent.md` | Stages files and writes a Conventional Commits message. Never pushes. |

See `.github/agents/` for the full agent files and `.github/copilot-instructions.md` for the project conventions they enforce.

---

## Licence

[Apache 2.0](LICENSE)
