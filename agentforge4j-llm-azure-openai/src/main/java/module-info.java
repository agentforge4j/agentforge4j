import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.azureopenai.AzureOpenAiLlmClientFactory;

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
