/**
 * LLM provider integration: client execution, service discovery, and configuration.
 * <p>
 * Defines a small SPI ({@link com.agentforge4j.llm.LlmClientFactory}) plus HTTP-oriented helpers
 * so provider modules can plug in without the core runtime depending on vendor SDKs.
 * <p>
 * Key types:
 * <ul>
 *   <li>{@link com.agentforge4j.llm.LlmClient} — executes requests for one provider id</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientFactory} — constructs clients from {@link com.agentforge4j.llm.LlmClientConfiguration}</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientResolver} — selects a client by provider id</li>
 *   <li>{@link com.agentforge4j.llm.LlmExecutionRequest} — immutable invocation parameters</li>
 *   <li>{@link com.agentforge4j.llm.LlmInvocationException} — failures from clients or transport</li>
 * </ul>
 */
package com.agentforge4j.llm;
