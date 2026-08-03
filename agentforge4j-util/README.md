# agentforge4j-util

Dependency-light validation helpers and shared primitives reused across the framework.

## Why it exists

Every higher layer — `core`, the LLM modules, the loaders, the runtime — needs the same small
set of argument and path checks. Keeping them here, with no runtime dependencies, lets those
layers share the behaviour without creating dependency cycles or pulling a third-party validation
library onto the runtime module graph. This module deliberately holds **no** domain or
orchestration types; it is the bottom of the dependency chain.

## How it fits

`agentforge4j-util` has no `agentforge4j` dependencies and sits below everything else. Its only
declared dependency is the SpotBugs nullness annotations, which are compile-only (`provided` +
`optional`, paired with `requires static` in `module-info`) and never reach the runtime.

## Key public types

| Type | Purpose |
|---|---|
| `Validate` | Final utility class of static argument and path validators. |

`Validate` exposes:

- `notBlank`, `notNull`, `notEmpty`, `isTrue` — argument guards, each with a message or an exception-supplier overload.
- `isBetween`, `isGreaterThan`, `isGreaterThanZero`, `isNotNegative` — numeric range guards.
- `requireDirectory` — asserts a path is a directory and normalises it.
- `requireWithinBase` — resolves a relative path against an absolute base and verifies it cannot escape the base, including via symlinks.

Pick the validator by intent — use `isGreaterThanZero` rather than `isNotNegative` when the
minimum is 1.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-util</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.util;
```

Exports `com.agentforge4j.util`, `com.agentforge4j.util.net`, `com.agentforge4j.util.retry`, and
`com.agentforge4j.util.text`.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
