# AgentForge4j-LLM Unit Tests - Quick Reference

## Test Files Location
```
src/test/java/com/agentforge4j/llm/
├── TestFixtures.java                  - Shared test builders and constants
├── LlmExecutionRequestTest.java       - Tests for LlmExecutionRequest record
├── LlmInvocationExceptionTest.java    - Tests for LlmInvocationException
├── DefaultLlmClientResolverTest.java  - Tests for DefaultLlmClientResolver (19 tests)
└── LlmClientImplTest.java            - Tests for LlmClientImpl abstract class (21 tests)
```

## Test Counts by File
- **TestFixtures.java** — 0 tests (test utility class)
- **LlmExecutionRequestTest.java** — 7 tests ✅
- **LlmInvocationExceptionTest.java** — 7 tests ✅
- **DefaultLlmClientResolverTest.java** — 19 tests ✅
  - ConstructorTests: 8 tests
  - ResolveTests: 9 tests
  - DiscoverTests: 2 tests
- **LlmClientImplTest.java** — 21 tests ✅
  - ConstructorTests: 6 tests
  - StripCodeFenceTests: 9 tests
  - ExecuteTests: 6 tests

**Total: 54 tests, all passing** ✅

## Running the Tests

### Run all tests in the module
```bash
mvn clean test -pl agentforge4j-llm
```

### Run a specific test class
```bash
mvn test -pl agentforge4j-llm -Dtest=LlmExecutionRequestTest
mvn test -pl agentforge4j-llm -Dtest=DefaultLlmClientResolverTest
```

### Run tests with more verbose output
```bash
mvn test -pl agentforge4j-llm -X
```

### Run with code coverage (JaCoCo)
```bash
mvn clean verify -pl agentforge4j-llm
```

## Test Fixtures Usage

### Building test configurations
```java
LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4", Duration.ofSeconds(30));
```

### Building test requests
```java
LlmExecutionRequest request = TestFixtures.testRequest("openai", "prompt", "input");
LlmExecutionRequest request = TestFixtures.testRequest("openai", "gpt-4", "prompt", "input");
LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("openai", "prompt", "input");
```

### Using test implementations
```java
LlmClient testClient = new TestFixtures.TestLlmClient("openai");
LlmClientFactory testFactory = new TestFixtures.TestLlmClientFactory("openai");
ObjectMapper mapper = TestFixtures.testObjectMapper();
```

## Key Test Patterns

### Validation Testing Pattern
Tests use `@Nested` classes to group validation scenarios:
```java
@Nested
class ConstructorTests {
  @Test
  void should_throw_on_null_parameter() { ... }
  
  @Test
  void should_throw_on_blank_parameter() { ... }
  
  @Test
  void should_succeed_with_valid_parameters() { ... }
}
```

### Test Naming Convention
All tests follow the `should_X_when_Y` naming pattern:
- `should_construct_with_all_fields` — positive case
- `should_throw_on_null_config` — negative/error case
- `should_normalize_provider_names_for_duplicates` — edge case

### Assertion Pattern
Every test has meaningful assertions:
```java
@Test
void should_construct_with_valid_config() {
  LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
  TestLlmClientImpl client = new TestLlmClientImpl(config);
  
  assertEquals("openai", client.getProviderName());
  assertEquals("gpt-4", client.getDefaultModel());
}
```

## Validation Coverage

### LlmExecutionRequest
- ✅ All record fields assigned correctly
- ✅ Factory method sets model to null
- ✅ Equality and hashCode contracts
- ✅ Empty strings allowed (no validation)

### LlmInvocationException
- ✅ Constructor with message only
- ✅ Constructor with message and cause
- ✅ Extends RuntimeException
- ✅ Cause chain preservation

### DefaultLlmClientResolver
- ✅ Constructor: null, blank, duplicate validation
- ✅ Provider name case normalization
- ✅ Client resolution with trimming and case-folding
- ✅ Error messages include available providers
- ✅ Static discover() method error handling

### LlmClientImpl
- ✅ Constructor: config validation (provider, model, timeout)
- ✅ stripCodeFence() utility: markdown removal, edge cases
- ✅ Request validation: null, blank, and field checks
- ✅ Field preservation and access via getters

## What Is NOT Tested

1. **HTTP/Network code** — LlmClientImpl.execute() does not include HTTP mocking
   - Use integration tests for this (add LlmClientImplIntegrationTest if needed)

2. **ServiceLoader mechanics** — DefaultLlmClientResolver.discover() uses real ServiceLoader
   - Test covers error handling when no services found

3. **Abstract method implementations** — buildHttpRequest() and validateAndExtractResponse()
   - Tested through concrete TestLlmClientImpl subclass

## Coding Standards Applied

✅ Java 17 features with no `var` keyword  
✅ Explicit types throughout  
✅ Braces on all control flow (including single-line bodies)  
✅ Records constructed directly (not mocked)  
✅ JUnit 5 only (@Test, @Nested from org.junit.jupiter.api)  
✅ Meaningful test names and descriptions  
✅ No empty or assertion-less tests  
✅ Proper use of Validate.* methods in production code  

## Developer Review Checklist

Before considering tests complete:

- [ ] Confirm test values match real-world usage scenarios
- [ ] Run with code coverage tool to verify line/branch coverage
- [ ] Check that error messages are helpful and include context
- [ ] Review boundary cases (empty strings, whitespace, nulls)
- [ ] Plan integration tests for HTTP/network execution if needed
- [ ] Verify no test state leaks between test methods
- [ ] Run on CI/CD pipeline to ensure reproducibility

## Build Information

- **Maven Module:** `agentforge4j-llm`
- **Java Version:** Java 17
- **Test Framework:** JUnit 5 (Jupiter)
- **Build Tool:** Maven 3.x
- **Status:** ✅ All tests passing
- **Test Time:** ~6 seconds total


