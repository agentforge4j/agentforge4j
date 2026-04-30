# Pending Changelog

## Summary
Implemented the `agentforge4j-llm` module with provider-agnostic LLM abstractions, including support for multiple LLM providers via factory pattern. Updated parent pom.xml to include the new module and configure Spring Boot dependencies. Enhanced review and javadoc agent instructions with additional guidance.

## Changes
- **Added** `agentforge4j-llm` module with LLM client abstractions:
  - `LlmClient` interface for executing LLM requests
  - `LlmClientFactory` for provider registration via ServiceLoader
  - `AbstractHttpLlmClient` template method implementation for HTTP-based providers
  - `DefaultLlmClientResolver` for provider resolution
  - `LlmClientConfiguration` for timeout and retry configuration
  - `LlmExecutionRequest` record for structured request parameters
  - `LlmInvocationException` for standardized error handling
  - Complete unit test coverage with JUnit 5 and AssertJ
- **Updated** parent `pom.xml`:
  - Added `agentforge4j-llm` module to modules list
  - Added Spring Boot dependency management (3.5.13)
  - Added Tomcat embed core version property (10.1.54)
  - Added Lombok version property (1.18.46)
  - Added Maven compiler release property
  - Added internal dependency management for `agentforge4j-util` and `agentforge4j-llm`
- **Updated** `.github/agents/review-agent.agent.md`:
  - Clarified distinction between strict violations and advisory findings
  - Added advisory review section for code quality concerns not covered by strict rules
  - Added advisory finding severity levels (ADVISORY-WARNING, ADVISORY-INFO)
- **Updated** `.github/agents/javadoc-agent.agent.md`:
  - Minor formatting fix (added trailing newline)
- **Updated** `.gitignore`:
  - Uncommented `*.iml` pattern to exclude IntelliJ IDE module files
- **Added** `.github/agents/changelog-agent.agent.md`:
  - New agent for automated changelog generation from Git diffs
- **Added** `.github/agents/module-quality-orchestrator.agent.md`:
  - New orchestrator agent defining workflow for module development (javadoc → tests → build → review → changelog)

## Modules Affected
- `agentforge4j-llm` (new)
- Parent pom.xml
- `.github/agents/`

## Suggested Commit Message
feat: add agentforge4j-llm module with provider-agnostic LLM abstractions

- Implement LlmClient interface and factory pattern for LLM provider support
- Add AbstractHttpLlmClient template for HTTP-based providers
- Implement LlmClientResolver with provider discovery via ServiceLoader
- Add LlmClientConfiguration with timeout and retry support
- Add LlmExecutionRequest record for structured request parameters
- Implement LlmInvocationException for error handling
- Add comprehensive unit test coverage
- Update parent pom.xml with agentforge4j-llm module and Spring Boot dependencies
- Enhance review-agent guidance with advisory finding categories
- Add changelog-agent and module-quality-orchestrator agents
- Uncomment *.iml in .gitignore

## Suggested Pull Request Description

### Summary
This PR completes the `agentforge4j-llm` module, a core component of the AgentForge4j framework providing provider-agnostic LLM abstractions. The module follows the established architecture patterns and is fully testable without external dependencies.

### Changes
- **New Module**: `agentforge4j-llm` with LLM client abstractions
  - `LlmClient` interface for standardized LLM execution
  - `LlmClientFactory` for pluggable provider support
  - `AbstractHttpLlmClient` template for common HTTP patterns
  - `DefaultLlmClientResolver` for provider discovery
  - Complete test coverage (4 test classes, 100% of new code)
  
- **Build Configuration**:
  - Added module to parent pom.xml
  - Configured Spring Boot dependency management (3.5.13)
  - Added version properties for Lombok and Tomcat dependencies
  
- **Developer Tooling**:
  - Enhanced review-agent with advisory finding categories
  - Added changelog-agent for automated changelog generation
  - Added module-quality-orchestrator for development workflow guidance
  - Improved .gitignore for IDE artifact handling

### Architecture
- No framework dependencies in core LLM module (follows util/core/llm separation)
- Provider-agnostic design via `LlmClientFactory` + `ServiceLoader`
- Structured error handling with custom exception type
- Configuration-driven approach via `LlmClientConfiguration`
- Template method pattern for HTTP client implementations

### Testing
- 4 new unit test classes covering all public APIs
- Tests use JUnit 5 and AssertJ
- No external service dependencies

### Notes
- Module ready for downstream provider implementations (OpenAI, Ollama, Claude, vLLM)
- Dependency chain maintained: `util` ← `llm` ← `config-loader` ← `runtime`
- All code follows project coding standards (Java 26, JPMS, no Lombok on records, explicit types)

