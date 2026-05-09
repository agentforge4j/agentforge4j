/**
 * Small shared primitives (validation helpers, common utilities) reused across modules.
 *
 * <p>Kept dependency-light so higher layers ({@code core}, {@code llm}, loaders, runtime) can share
 * behaviour without creating cycles. Not a home for domain or orchestration types.
 */
module agentforge4j.util {
  exports com.agentforge4j.util;
  requires org.apache.commons.lang3;
}
