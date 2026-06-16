// SPDX-License-Identifier: Apache-2.0
/**
 * Amazon Bedrock LLM integration for AgentForge4J.
 * <p>
 * This package uses the official AWS SDK for Java v2
 * ({@code software.amazon.awssdk:bedrockruntime}) and
 * {@link software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient}. Authentication
 * follows the AWS default credentials provider chain.
 * <p>
 * Multiple model families are supported within this single provider. A request's model id is
 * resolved to a family by {@code BedrockModelRegistry}, which selects an internal transport:
 * <strong>Anthropic Claude</strong> ({@code anthropic.} prefix) is invoked via {@code InvokeModel}
 * with prompt caching and token reporting; other families (<strong>Llama</strong>,
 * <strong>Nova</strong>, <strong>Titan</strong>) are invoked via {@code Converse} for synchronous
 * text generation and token reporting.
 */
package com.agentforge4j.llm.bedrock;
