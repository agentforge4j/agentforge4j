import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.vllm.VllmLlmClientFactory;

module agentforge4j.llm.vllm {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires org.apache.commons.lang3;
  requires static lombok;
  exports com.agentforge4j.llm.vllm;
  uses com.agentforge4j.llm.LlmClientFactory;
  opens com.agentforge4j.llm.vllm.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
      with VllmLlmClientFactory;
}
