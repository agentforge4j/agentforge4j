/**
 * Provider-neutral LLM invocation contract: request parameters, client interface, and caller-facing
 * failures. Implementation modules ({@code agentforge4j.llm} and provider adapters) depend on this
 * artifact; this module does not depend on them.
 */
module agentforge4j.llm.api {
  requires agentforge4j.util;

  exports com.agentforge4j.llm.api;
}
