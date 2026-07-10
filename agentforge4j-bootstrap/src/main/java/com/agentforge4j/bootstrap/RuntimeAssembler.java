// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.agent.ArtifactValidatorFactory;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.ToolCatalog;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.runtime.WorkflowRuntimeBuilder;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Assembles the {@link AgentInvoker} and {@link WorkflowRuntime} from resolved components. Internal — not part of the
 * public API.
 */
final class RuntimeAssembler {

  private static final System.Logger LOGGER = System.getLogger(RuntimeAssembler.class.getName());

  private RuntimeAssembler() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Wraps {@code resolver} with {@link RetryingLlmClientResolver} when {@code retryPolicy} is non-null,
   * {@code retryPolicy.maxAttempts() > 1}, and {@code resolverWasExplicit} is false.
   *
   * @param resolver            base resolver; must not be {@code null}
   * @param retryPolicy         optional retry policy
   * @param resolverWasExplicit true if the caller provided an explicit resolver
   *
   * @return wrapped or original resolver; never {@code null}
   */
  static LlmClientResolver applyRetryPolicy(LlmClientResolver resolver,
      LlmRetryPolicy retryPolicy,
      boolean resolverWasExplicit) {
    if (retryPolicy != null
        && retryPolicy.maxAttempts() > 1
        && !resolverWasExplicit) {
      LlmClientResolver wrapped = new RetryingLlmClientResolver(resolver, retryPolicy);
      LOGGER.log(System.Logger.Level.INFO,
          "LLM resolver wrapped with RetryingLlmClientResolver (maxAttempts={0}).",
          retryPolicy.maxAttempts());
      return wrapped;
    }
    return resolver;
  }

  /**
   * Logs a WARNING when no LLM providers were resolved.
   *
   * @param llmClients          resolved client list
   * @param resolverWasExplicit true if the caller provided an explicit resolver
   */
  static void warnIfNoProviders(List<LlmClient> llmClients, boolean resolverWasExplicit) {
    if (!resolverWasExplicit && llmClients.isEmpty()) {
      LOGGER.log(System.Logger.Level.WARNING,
          """
              No LLM providers configured. Workflows that invoke agents will fail at runtime. \
              Set AGENTFORGE4J_LLM_<PROVIDER>_API_KEY, agentforge4j.llm.<provider>.api-key, \
              or use withLlmProvider(...).""");
    }
  }

  /**
   * Builds the default {@link AgentInvoker}. Logs a WARNING when both an explicit invoker and {@code cacheEnabled} were
   * configured (cache setting ignored).
   *
   * @param agentRepository              must not be {@code null}
   * @param llmClientResolver            must not be {@code null}
   * @param contextRenderer              must not be {@code null}
   * @param llmCommandParser             must not be {@code null}
   * @param objectMapper                 must not be {@code null}
   * @param eventRecorder                must not be {@code null}
   * @param llmProviderSelectionStrategy must not be {@code null}
   * @param cacheEnabled                 whether prompt caching is active
   * @param llmCallObserver              must not be {@code null}
   * @param modelTierResolver            must not be {@code null}
   * @param explicitInvoker              caller-provided invoker or {@code null} for default
   * @param cacheEnabledSet              true if {@code withCacheEnabled} was called explicitly
   *
   * @return resolved invoker; never {@code null}
   */
  static AgentInvoker agentInvoker(AgentRepository agentRepository,
      LlmClientResolver llmClientResolver,
      ContextRenderer contextRenderer,
      LlmCommandParser llmCommandParser,
      ObjectMapper objectMapper,
      EventRecorder eventRecorder,
      LlmProviderSelectionStrategy llmProviderSelectionStrategy,
      boolean cacheEnabled,
      LlmCallObserver llmCallObserver,
      ModelTierResolver modelTierResolver,
      AgentInvoker explicitInvoker,
      boolean cacheEnabledSet,
      ToolCatalog toolCatalog,
      RunExecutionInterceptor runExecutionInterceptor) {
    if (explicitInvoker != null && cacheEnabledSet) {
      LOGGER.log(System.Logger.Level.WARNING,
          """
              Both withAgentInvoker and withCacheEnabled were called; \
              withCacheEnabled ignored because explicit AgentInvoker was provided.""");
    }

    if (explicitInvoker != null) {
      return explicitInvoker;
    }

    return AgentInvoker.builder()
        .agentRepository(agentRepository)
        .llmClientResolver(llmClientResolver)
        .contextRenderer(contextRenderer)
        .llmCommandParser(llmCommandParser)
        .objectMapper(objectMapper)
        .eventRecorder(eventRecorder)
        .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
        .llmProviderSelectionStrategy(llmProviderSelectionStrategy)
        .promptCacheEnabled(cacheEnabled)
        .llmCallObserver(llmCallObserver)
        .modelTierResolver(modelTierResolver)
        .toolCatalog(toolCatalog)
        .runExecutionInterceptor(runExecutionInterceptor)
        .build();
  }

  /**
   * Builds the {@link WorkflowRuntime} from all resolved components.
   *
   * @param workflowRepository      must not be {@code null}
   * @param workflowStateRepository must not be {@code null}
   * @param workflowEventLog        must not be {@code null}
   * @param clock                   must not be {@code null}
   * @param fileSink                must not be {@code null}
   * @param agentInvoker            must not be {@code null}
   * @param eventRecorder           must not be {@code null}
   * @param maxNestingDepth         optional nesting depth override
   * @param requirementResolver     optional requirement resolver; when {@code null} the runtime builder defaults to its
   *                                in-process {@code DefaultRequirementResolver}
   * @param runExecutionInterceptor optional run-execution interceptor; when {@code null} the runtime builder defaults
   *                                to its {@code NO_OP} interceptor
   *
   * @return assembled runtime; never {@code null}
   */
  static WorkflowRuntime runtime(WorkflowRepository workflowRepository,
      WorkflowStateRepository workflowStateRepository,
      WorkflowEventLog workflowEventLog,
      Clock clock,
      FileSink fileSink,
      AgentInvoker agentInvoker,
      EventRecorder eventRecorder,
      Integer maxNestingDepth,
      ToolExecutionService toolExecutionService,
      PendingToolInvocationStore pendingToolInvocationStore,
      RequirementResolver requirementResolver,
      RunExecutionInterceptor runExecutionInterceptor,
      ObjectMapper objectMapper,
      List<ArtifactValidator> embedderArtifactValidators) {
    // Built-in ArtifactValidators are discovered via ServiceLoader (the built-in agent-bundle validator stays present
    // so shipped agent-bundle workflows keep working); each factory receives the same configured ObjectMapper the agent
    // loaders use, so validation parses in lockstep with production load. Embedder-supplied validators are appended; a
    // duplicate validator id fails fast in the runtime.
    List<ArtifactValidator> artifactValidators = new ArrayList<>();
    ServiceLoader.load(ArtifactValidatorFactory.class, Thread.currentThread().getContextClassLoader())
        .forEach(factory -> artifactValidators.add(factory.create(objectMapper)));
    artifactValidators.addAll(embedderArtifactValidators);
    WorkflowRuntimeBuilder runtimeBuilder = new WorkflowRuntimeBuilder()
        .workflowRepository(workflowRepository)
        .workflowStateRepository(workflowStateRepository)
        .workflowEventLog(workflowEventLog)
        .clock(clock)
        .fileSink(fileSink)
        .agentInvoker(agentInvoker)
        .eventRecorder(eventRecorder)
        .runExecutionInterceptor(runExecutionInterceptor)
        .artifactValidators(List.copyOf(artifactValidators))
        // Without this, WorkflowRuntimeBuilder defaults to its own bare new ObjectMapper() for
        // context-selection JSON handling (ledger content, compact siblings), diverging from the
        // mapper configuration loading and artifact validation above already use.
        .objectMapper(objectMapper);

    if (maxNestingDepth != null) {
      runtimeBuilder.maxNestingDepth(maxNestingDepth);
    }
    if (toolExecutionService != null) {
      runtimeBuilder.toolExecutionService(toolExecutionService);
    }
    if (pendingToolInvocationStore != null) {
      runtimeBuilder.pendingToolInvocationStore(pendingToolInvocationStore);
    }
    if (requirementResolver != null) {
      runtimeBuilder.requirementResolver(requirementResolver);
    }

    return runtimeBuilder.build();
  }
}
