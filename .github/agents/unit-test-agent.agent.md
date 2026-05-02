Add or improve tests for module: "[MODULE_NAME]".

Goal

Create meaningful test coverage for this module.

Focus on:

- public API behaviour
- all important method scenarios
- edge cases
- negative paths
- validation failures
- exception handling
- provider/request mapping if applicable

Do NOT create shallow tests just to increase coverage.

---

First inspect existing tests

Before adding new tests:

1. inspect existing test classes
2. check if they are still correct
3. remove or fix weak/incorrect tests
4. add only missing meaningful tests

---

Test naming rules

Unit tests

Class names ending in:

Test

Rules:

- may use Mockito/mocking
- should test one class or behaviour in isolation
- should be fast
- should not call real external services

Example:

OpenAiLlmClientTest
ValidateTest
LlmExecutionRequestTest

---

Integration tests

Class names ending in:

IT

Rules:

- should avoid mocking
- should test real wiring/behaviour where useful
- should not call real paid/external providers
- for provider modules, use fake/local HTTP server or fake provider endpoint returning deterministic responses
- should be runnable in normal Maven lifecycle only if current project convention allows it

Example:

OpenAiLlmClientIT
LlmProviderConfigurationIT

If integration test infrastructure is not ready, list recommended IT scenarios but do not force complex setup.

---

Provider module testing rules

For LLM provider modules:

- do NOT call real OpenAI/Claude/Gemini/etc.
- use a fake HTTP server or fake provider response
- verify:
    - request body mapping
    - headers/auth if applicable
    - model/provider config handling
    - successful response parsing
    - error response handling
    - timeout/failure behaviour if supported
    - invalid/malformed response handling

---

Unit test coverage expectations

For each public class/method:

Test:

- happy path
- null/blank/invalid input
- boundary values
- unsupported values
- exception paths
- immutability/defensive copying if relevant

For utility classes:

- test all branches
- test invalid inputs heavily
- test error messages when useful

For DTOs/records:

- only test validation, defaults, or behaviour
- do not write pointless getter/constructor tests

---

Style requirements

- Use JUnit 5
- Use AssertJ if already used in the project, otherwise use existing assertion style
- Use Mockito only in "*Test", not "*IT"
- Follow existing package and naming conventions
- Keep tests readable
- Use descriptive test method names
- Prefer one clear behaviour per test
- Avoid over-mocking
- Avoid testing private methods directly

---

Maven/build requirements

After adding tests:

1. run the relevant module tests
2. fix compilation/test failures
3. ensure no real provider API keys are required
4. ensure tests are deterministic

---

Output

Provide:

- summary of existing tests reviewed
- tests added/changed
- key scenarios covered
- any scenarios intentionally left for later integration tests
