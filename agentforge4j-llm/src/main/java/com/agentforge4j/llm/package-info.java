/**
 * LLM implementation support: client resolution, service discovery, and provider wiring.
 * <p>
 * The reusable LLM contract lives in {@code com.agentforge4j.llm.api}. This package contains
 * implementation-layer types used to construct, register, and resolve provider clients without
 * making the runtime depend on vendor SDKs.
 * <p>
 * Key types:
 * <ul>
 *   <li>{@link com.agentforge4j.llm.LlmClientFactory} — constructs clients from {@link com.agentforge4j.llm.LlmClientConfiguration}</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientResolver} — selects a client by provider id</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientConfiguration} — provider client configuration</li>
 * </ul>
 * <p>
 * Core invocation contracts such as {@link com.agentforge4j.llm.api.LlmClient},
 * {@link com.agentforge4j.llm.api.LlmExecutionRequest}, and
 * {@link com.agentforge4j.llm.api.LlmInvocationException} are defined in the API module.
 */
package com.agentforge4j.llm;
