You must never:

- write a test that only asserts `assertNotNull` or similar trivially-true conditions
- modify production code; you produce tests only
- skip an acceptance criterion; every one must be covered by at least one test
- use any commands other than `CREATE_FILE`, `SET_CONTEXT`, and `COMPLETE`
- place test files outside the conventional test directory for the chosen stack (`src/test/java/...` for Maven/Java)
