import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.ollama.OllamaLlmClientFactory;

module agentforge4j.llm.ollama {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires java.net.http;
  requires static lombok;
  opens com.agentforge4j.llm.ollama.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
    with OllamaLlmClientFactory;
}
