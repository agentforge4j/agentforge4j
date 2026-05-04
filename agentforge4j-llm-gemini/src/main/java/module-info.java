import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.gemini.GeminiLlmClientFactory;

module agentforge4j.llm.gemini {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  exports com.agentforge4j.llm.gemini;
  opens com.agentforge4j.llm.gemini.dto to com.fasterxml.jackson.databind;
  uses LlmClientFactory;
  provides LlmClientFactory
    with GeminiLlmClientFactory;
}
