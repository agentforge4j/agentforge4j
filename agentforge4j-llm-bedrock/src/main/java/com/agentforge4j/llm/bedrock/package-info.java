/**
 * Amazon Bedrock LLM integration for AgentForge4J.
 * <p>
 * This package uses the official AWS SDK for Java v2 ({@code software.amazon.awssdk:bedrockruntime})
 * and {@link software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient} to call
 * {@code InvokeModel}. Authentication follows the AWS default credentials provider chain.
 * <p>
 * The initial implementation supports <strong>Anthropic Claude</strong> models on Bedrock only
 * (model IDs with the {@code anthropic.} prefix), not other Bedrock model families.
 */
package com.agentforge4j.llm.bedrock;
