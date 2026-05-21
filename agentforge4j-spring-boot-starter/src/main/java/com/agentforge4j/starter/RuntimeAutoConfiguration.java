package com.agentforge4j.starter;

import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.RunContextManager;
import com.agentforge4j.runtime.WorkflowRuntimeBuilder;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.starter.files.NoOpFileSink;
import com.agentforge4j.starter.logging.MdcRunContextManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the {@link WorkflowRuntime} itself, plus the {@link Clock} the rest of the framework uses
 * for timestamps. Scheduled last in the chain — depends on repositories, the event log, and the LLM
 * resolver.
 */
@AutoConfiguration(after = {
    RepositoryAutoConfiguration.class,
    LlmAutoConfiguration.class
})
@EnableConfigurationProperties(LlmCacheSettings.class)
public class RuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public RunContextManager runContextManager() {
    return new MdcRunContextManager();
  }

  @Bean
  @ConditionalOnMissingBean
  public SchemaProvider schemaProvider() {
    return new ClasspathSchemaProvider();
  }

  @Bean
  @ConditionalOnMissingBean(FileSink.class)
  public FileSink fileSink() {
    return new NoOpFileSink();
  }

  @Bean
  @ConditionalOnMissingBean
  public EventRecorder eventRecorder(WorkflowEventLog workflowEventLog, Clock clock) {
    return new EventRecorder(workflowEventLog, clock);
  }

  /**
   * Builds the runtime {@link AgentInvoker}, forwarding {@link LlmCacheSettings#enabled()} as
   * {@code promptCacheEnabled}.
   */
  @Bean
  @ConditionalOnMissingBean
  public AgentInvoker agentInvoker(AgentRepository agentRepository,
      LlmClientResolver llmClientResolver,
      ObjectMapper objectMapper,
      EventRecorder eventRecorder,
      LlmCacheSettings cacheSettings) {
    LlmCallObserver llmCallObserver = new LlmCallObserver(eventRecorder);
    return new AgentInvoker(
        agentRepository,
        llmClientResolver,
        new ContextRenderer(objectMapper),
        new LlmCommandParser(objectMapper),
        objectMapper,
        eventRecorder,
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP,
        new FirstAvailableProviderSelectionStrategy(),
        cacheSettings.enabled(),
        llmCallObserver);
  }

  /**
   * Constructs {@link com.agentforge4j.core.runtime.WorkflowRuntime} from injected collaborators,
   * applies {@link AgentForge4jProperties#maxNestingDepth()} when non-null, and substitutes
   * {@link com.agentforge4j.integrations.NoOpIntegrationRegistry} when no
   * {@linkplain com.agentforge4j.integrations.IntegrationRegistry integration registry} bean
   * exists.
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowRuntime workflowRuntime(WorkflowRepository workflowRepository,
      WorkflowStateRepository workflowStateRepository,
      WorkflowEventLog workflowEventLog,
      Clock clock,
      RunContextManager runContextManager,
      AgentForge4jProperties properties,
      FileSink fileSink,
      AgentInvoker agentInvoker,
      EventRecorder eventRecorder,
      Optional<IntegrationRegistry> integrationRegistry) {
    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
        .workflowRepository(workflowRepository)
        .workflowStateRepository(workflowStateRepository)
        .workflowEventLog(workflowEventLog)
        .clock(clock)
        .runContextManager(runContextManager)
        .fileSink(fileSink)
        .agentInvoker(agentInvoker)
        .eventRecorder(eventRecorder)
        .integrationRegistry(integrationRegistry.orElse(NoOpIntegrationRegistry.INSTANCE));
    if (properties.maxNestingDepth() != null) {
      builder.maxNestingDepth(properties.maxNestingDepth());
    }
    return builder.build();
  }
}
