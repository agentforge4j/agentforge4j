import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.bedrock.BedrockLlmClientFactory;

module agentforge4j.llm.bedrock {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires static jdk.httpserver;
  requires org.apache.commons.lang3;
  requires static lombok;
  requires software.amazon.awssdk.awscore;
  requires software.amazon.awssdk.core;
  requires software.amazon.awssdk.auth;
  requires software.amazon.awssdk.http;
  requires software.amazon.awssdk.http.urlconnection;
  requires software.amazon.awssdk.regions;
  requires software.amazon.awssdk.services.bedrockruntime;
  uses LlmClientFactory;
  provides LlmClientFactory
      with BedrockLlmClientFactory;
}
