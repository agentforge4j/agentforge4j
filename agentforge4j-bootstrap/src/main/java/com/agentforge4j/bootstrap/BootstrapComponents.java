// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.spi.integration.IntegrationRepository;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
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
 * <p>A framework integrator uses this record to register the individual components without
 * duplicating bootstrap's default-wiring logic.
 *
 * <p>Tool support is opt-in, so {@code integrationRepository}, {@code toolProviderResolver}, and
 * {@code toolExecutionService} are {@code null} unless an integrations source (or explicit resolver / providers) was
 * configured; {@code integrationRepository} is non-null only on the integrations path, where it is the repository
 * feeding the resolver. All other components are never {@code null}.
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
    IntegrationRepository integrationRepository,
    ToolProviderResolver toolProviderResolver,
    ToolExecutionService toolExecutionService,
    ObjectMapper objectMapper,
    Clock clock,
    AgentInvoker agentInvoker,
    LlmCallObserver llmCallObserver,
    LoadedConfiguration loadedConfiguration
) {

}
