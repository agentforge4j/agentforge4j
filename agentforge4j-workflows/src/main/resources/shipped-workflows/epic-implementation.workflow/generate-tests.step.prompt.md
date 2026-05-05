## Step: Generate Test Cases

Given `epicScope` and the just-produced files for this epic (in `generatedFiles`), produce real test files covering every acceptance criterion plus the relevant edge and failure scenarios.

### Hard rules

- One test class per production class that has non-trivial behaviour (controllers, services, key validators).
- Map every acceptance criterion to at least one test method. Use the criterion text as the test method name (camelCased) or as the `@DisplayName`.
- Cover edge cases: null/empty inputs, boundary values, unauthorised access where relevant, concurrent or duplicate-key scenarios where the data model implies them.
- Cover failure scenarios: invalid input rejected with the right status, missing dependencies, integration failures.
- For Java + Spring Boot: use JUnit 5, Mockito, AssertJ. Use `@WebMvcTest` for controllers, plain unit tests with mocks for services. No `@SpringBootTest` unless the architecture truly requires it.
- Do not modify production files. Tests only.

### Output

One `CREATE_FILE` per test file. Then one `SET_CONTEXT` writing the **complete merged** `generatedTests` list (cumulative, including this epic's tests) as a JsonContextValue:

```json
[
  {"path": "string", "epicId": "string", "covers": ["storyId.criterionRef"]}
]
```

Then `COMPLETE`.
