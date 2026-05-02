import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.openai.OpenAiLlmClientFactory;

module agentforge4j.llm.openai {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires static lombok;
  requires org.apache.commons.lang3;
  exports com.agentforge4j.llm.openai.dto;
  provides LlmClientFactory
      with OpenAiLlmClientFactory;
}
