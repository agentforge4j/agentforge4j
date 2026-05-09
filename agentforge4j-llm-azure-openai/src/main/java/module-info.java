import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.azureopenai.AzureOpenAiLlmClientFactory;

/**
 * Azure OpenAI Service HTTP adapter providing {@link LlmClientFactory} for enterprise deployments.
 *
 * <p>Handles Azure-specific URL layout, API versions, and payload quirks while normalizing into the
 * shared {@code agentforge4j.llm} client API consumed by applications through that abstraction.
 */
module agentforge4j.llm.azureopenai {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  opens com.agentforge4j.llm.azureopenai.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
      with AzureOpenAiLlmClientFactory;
}
