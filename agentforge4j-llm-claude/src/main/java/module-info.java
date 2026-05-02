import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.claude.ClaudeLlmClientFactory;

module agentforge4j.llm.claude {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires static lombok;
  requires org.apache.commons.lang3;
  opens com.agentforge4j.llm.claude.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
      with ClaudeLlmClientFactory;
}
