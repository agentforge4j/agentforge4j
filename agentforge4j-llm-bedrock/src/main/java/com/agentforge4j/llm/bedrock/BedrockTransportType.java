package com.agentforge4j.llm.bedrock;

/**
 * Internal Bedrock invocation transports. The dispatcher selects a transport by this type,
 * decoupled from {@link BedrockModelFamily} so a family can be retargeted to a different transport
 * as data.
 */
enum BedrockTransportType {
  INVOKE_MODEL,
  CONVERSE
}
