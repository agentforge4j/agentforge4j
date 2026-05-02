Add or improve tests for module: "[MODULE_NAME]".

## Goal

Create **production-ready automated test coverage** for this module.

The project must be releasable using **only automated tests**.

You are working in **test-first mode**.

---

## 🚨 CRITICAL RULE

**DO NOT modify production code.**

You are ONLY allowed to:

* add tests
* update existing tests
* remove incorrect tests

If something in production code prevents proper testing:

👉 DO NOT change it
👉 DO NOT “fix” it

Instead:

```text
Explain exactly:
- what cannot be tested
- why it cannot be tested
- what minimal change would be required
- why that change is safe
```

---

## First inspect existing tests

Before adding new tests:

1. inspect existing test classes
2. check correctness
3. remove/fix weak tests
4. keep strong tests
5. add only missing meaningful tests

---

## Test strategy (MANDATORY DECISION)

You must decide:

```text
Are integration tests (*IT) required?
```

### Required if module contains:

* HTTP clients
* provider integrations
* serialization/deserialization
* external boundary logic
* configuration wiring

### Not required if:

* pure logic/util module

If required:
→ create *IT tests

If not:
→ explain clearly why not

---

## Test naming rules

### Unit tests → *Test

* may use Mockito
* isolate behaviour
* fast
* no external calls

### Integration tests → *IT

* NO mocking
* use local fake server if needed
* test real wiring
* must be deterministic
* must not require API keys

---

## Provider module rules

For LLM providers:

* NEVER call real providers
* use fake/local HTTP server

Test:

* request mapping
* headers/auth
* response parsing
* error handling
* malformed responses
* timeout behaviour

---

## ❗ If testability is blocked

If you cannot properly test something:

DO NOT modify code.

Instead output:

```text
TESTABILITY GAP:
- Problem:
- Why it matters:
- Suggested minimal change:
- Example fix:
```

---

## Coverage expectations

Test:

* happy path
* edge cases
* invalid inputs
* exception paths

Avoid:

* trivial tests
* getter-only tests

---

## Style

* JUnit 5
* AssertJ if present
* Mockito only in *Test
* no private method testing
* readable tests

---

## Build requirements

* tests must pass
* no external dependencies
* deterministic

---

## Output

Provide:

* summary of existing tests
* decision on *IT necessity
* tests added/changed
* coverage summary
* TESTABILITY GAP section (if any)

DO NOT modify production code.
