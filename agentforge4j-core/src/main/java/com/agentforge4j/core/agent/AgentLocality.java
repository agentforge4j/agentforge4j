// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

/**
 * Specifies where an agent executes, determining the operational environment.
 */
public enum AgentLocality {
    /**
     * Agent runs locally, typically under operator control (e.g., Ollama).
     */
    LOCAL,
    /**
     * Agent runs in the cloud, using external APIs (e.g., OpenAI).
     */
    CLOUD
}
