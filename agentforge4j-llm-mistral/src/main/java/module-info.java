import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.mistral.MistralLlmClientFactory;

/**
 * Mistral AI HTTP adapter registered as an {@link LlmClientFactory} provider.
 *
 * <p>Keeps Mistral chat JSON and endpoint conventions out of {@code agentforge4j.core} and
 * {@code agentforge4j.llm} while preserving the deterministic request lifecycle expected of the shared client API.
 */
module agentforge4j.llm.mistral {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  opens com.agentforge4j.llm.mistral.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
    with MistralLlmClientFactory;
}
