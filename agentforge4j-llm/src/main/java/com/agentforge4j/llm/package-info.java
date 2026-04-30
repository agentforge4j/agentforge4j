/**
 * Provides abstractions for LLM providerName integration and client management.
 * <p>
 * This package defines the core interfaces and implementations for interacting with
 * Large Language Model providers such as OpenAI, Ollama, Claude, and vLLM. It provides
 * a unified API for executing LLM requests while allowing providerName-specific implementations
 * to handle the details of HTTP communication and response parsing.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.agentforge4j.llm.LlmClient} — executes LLM requests against a specific providerName</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientFactory} — creates providerName-specific client instances</li>
 *   <li>{@link com.agentforge4j.llm.LlmClientResolver} — resolves providers by name</li>
 *   <li>{@link com.agentforge4j.llm.LlmExecutionRequest} — immutable request data</li>
 *   <li>{@link com.agentforge4j.llm.LlmInvocationException} — thrown on LLM request failures</li>
 * </ul>
 */
package com.agentforge4j.llm;
