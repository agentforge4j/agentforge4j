import com.agentforge4j.llm.LlmClientFactory;

module agentforge4j.llm {
  requires com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires java.net.http;
  requires static lombok;
  requires agentforge4j.util;
  exports com.agentforge4j.llm;
  uses LlmClientFactory;
}
