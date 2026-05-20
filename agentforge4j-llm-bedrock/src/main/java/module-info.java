import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.bedrock.BedrockLlmClientFactory;

/**
 * AWS Bedrock Runtime client adapter implementing {@link LlmClientFactory} with AWS SDK v2.
 *
 * <p>Isolates AWS signing, regional endpoints, and Bedrock model IDs from portable workflow code.
 * Pulls in AWS modules explicitly so deployments only pay the dependency when this module is
 * present.
 */
module agentforge4j.llm.bedrock {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static jdk.httpserver;
  requires org.apache.commons.lang3;
  requires static lombok;
  opens com.agentforge4j.llm.bedrock.dto to com.fasterxml.jackson.databind;
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
