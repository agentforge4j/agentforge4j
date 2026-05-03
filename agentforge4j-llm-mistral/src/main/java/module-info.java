import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.mistral.MistralLlmClientFactory;

module agentforge4j.llm.mistral {
  requires agentforge4j.llm;
  requires agentforge4j.llm.openai;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  exports com.agentforge4j.llm.mistral;
  provides LlmClientFactory
    with MistralLlmClientFactory;
}
