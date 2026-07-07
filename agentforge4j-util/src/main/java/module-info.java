// SPDX-License-Identifier: Apache-2.0
/**
 * Small shared primitives (validation helpers, common utilities) reused across modules.
 *
 * <p>Kept dependency-light so higher layers ({@code core}, {@code llm}, loaders, runtime) can share
 * behaviour without creating cycles. Not a home for domain or orchestration types.
 */
module agentforge4j.util {
  // Compile-only: SpotBugs nullness annotations. `static` keeps it off the runtime module graph.
  requires static com.github.spotbugs.annotations;

  exports com.agentforge4j.util;
  exports com.agentforge4j.util.net;
  exports com.agentforge4j.util.retry;
}
