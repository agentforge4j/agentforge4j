package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;

/**
 * Exposes the individual components assembled by {@link AgentForge4jBootstrap}.
 *
 * <p><strong>Internal — for framework integrators only (Spring starter, Quarkus extension,
 * CLI).</strong> Not part of the public API. Not advertised in the README.
 *
 * <p>The Spring starter uses this record to register individual Spring beans without
 * duplicating bootstrap's default-wiring logic.
 */
public record BootstrapComponents(
    AgentRepository agentRepository,
    WorkflowRepository workflowRepository,
    WorkflowStateRepository workflowStateRepository,
    WorkflowEventLog workflowEventLog,
    LlmClientResolver llmClientResolver,
    ContextRenderer contextRenderer,
    LlmCommandParser llmCommandParser,
    EventRecorder eventRecorder,
    FileSink fileSink,
    LlmProviderSelectionStrategy llmProviderSelectionStrategy,
    IntegrationRegistry integrationRegistry,
    ObjectMapper objectMapper,
    Clock clock,
    AgentInvoker agentInvoker,
    LlmCallObserver llmCallObserver,
    LoadedConfiguration loadedConfiguration
) {

}
