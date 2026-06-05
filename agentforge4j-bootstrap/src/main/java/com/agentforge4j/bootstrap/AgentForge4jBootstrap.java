package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.llm.ConfigModelTierResolver;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.runtime.WorkflowRuntimeBuilder;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Static entry point for assembling an {@link AgentForge4j} facade with framework-agnostic
 * defaults.
 *
 * <pre>{@code
 * AgentForge4j af = AgentForge4jBootstrap.defaults().build();
 * }</pre>
 */
public final class AgentForge4jBootstrap {

  private AgentForge4jBootstrap() {
    throw new UnsupportedOperationException("static entry point");
  }

  /**
   * Returns a new {@link Builder} pre-populated with all framework defaults. No arguments are
   * required; the returned builder produces a fully functional {@link AgentForge4j} instance when
   * {@link Builder#build()} is called without any overrides.
   *
   * @return new builder; never {@code null}
   */
  public static Builder defaults() {
    return new Builder();
  }

  /**
   * Builder for {@link AgentForge4j}. Obtain via {@link AgentForge4jBootstrap#defaults()}.
   *
   * <p>All {@code with*(null)} calls throw {@link IllegalArgumentException} immediately —
   * passing null is always a bug. To restore a default, build a new instance.
   *
   * <p>{@link #withLlmProvider(LlmProviderConfig)} is <em>additive</em> across providers
   * and last-write-wins within a provider key. All other {@code with*} methods are
   * last-write-wins.
   */
  public static final class Builder {

    private Clock clock;
    private ObjectMapper objectMapper;
    private AgentRepository agentRepository;
    private WorkflowRepository workflowRepository;
    private WorkflowStateRepository workflowStateRepository;
    private WorkflowEventLog workflowEventLog;
    private IntegrationRegistry integrationRegistry;
    private LlmClientResolver llmClientResolver;
    private LlmRetryPolicy llmRetryPolicy;
    private ContextRenderer contextRenderer;
    private LlmCommandParser llmCommandParser;
    private EventRecorder eventRecorder;
    private FileSink fileSink;
    private LlmProviderSelectionStrategy llmProviderSelectionStrategy;
    private AgentInvoker agentInvoker;
    private LlmCallObserver llmCallObserver;
    private ModelTierResolver modelTierResolver;

    private boolean cacheEnabled = false;
    private boolean cacheEnabledSet = false;
    private Integer maxNestingDepth;

    private boolean loadShippedAgents = true;
    private boolean loadShippedAgentsSet = false;
    private boolean loadShippedWorkflows = true;
    private boolean loadShippedWorkflowsSet = false;
    private Path agentsDir;
    private Path workflowsDir;
    private Path fileSinkPath;

    private final Map<String, LlmProviderConfig> llmProviders = new LinkedHashMap<>();

    Builder() {
      // package-private construction via AgentForge4jBootstrap.defaults() only
    }

    /**
     * Overrides the clock used for timestamps on state updates and events.
     *
     * @param clock clock instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withClock(Clock clock) {
      this.clock = Validate.notNull(clock, "clock must not be null");
      return this;
    }

    /**
     * Overrides the Jackson {@link ObjectMapper} used for configuration loading and LLM parsing.
     *
     * @param objectMapper mapper instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withObjectMapper(ObjectMapper objectMapper) {
      this.objectMapper = Validate.notNull(objectMapper, "objectMapper  must not be null");
      return this;
    }

    /**
     * Overrides the agent definition repository.
     *
     * @param agentRepository repository instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withAgentRepository(AgentRepository agentRepository) {
      this.agentRepository = Validate.notNull(agentRepository, "agentRepository must not be null");
      return this;
    }

    /**
     * Overrides the workflow definition repository.
     *
     * @param workflowRepository repository instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withWorkflowRepository(WorkflowRepository workflowRepository) {
      this.workflowRepository = Validate.notNull(workflowRepository,
          "workflowRepository must not be null");
      return this;
    }

    /**
     * Overrides persistence for workflow state between drives.
     *
     * @param workflowStateRepository repository instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withWorkflowStateRepository(WorkflowStateRepository workflowStateRepository) {
      this.workflowStateRepository = Validate.notNull(workflowStateRepository,
          "workflowStateRepository must not be null");
      return this;
    }

    /**
     * Overrides the append-only workflow event log.
     *
     * @param workflowEventLog event log instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withWorkflowEventLog(WorkflowEventLog workflowEventLog) {
      this.workflowEventLog = Validate.notNull(workflowEventLog,
          "workflowEventLog must not be null");
      return this;
    }

    /**
     * Overrides the integration registry for {@code CallEndpointCommand} resolution.
     *
     * @param integrationRegistry registry instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withIntegrationRegistry(IntegrationRegistry integrationRegistry) {
      this.integrationRegistry = Validate.notNull(integrationRegistry,
          "integrationRegistry must not be null");
      return this;
    }

    /**
     * Overrides the LLM client resolver.
     *
     * @param llmClientResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmClientResolver(LlmClientResolver llmClientResolver) {
      this.llmClientResolver = Validate.notNull(llmClientResolver,
          "llmClientResolver must not be null");
      return this;
    }

    /**
     * Configures the LLM retry policy. When {@code maxAttempts > 1}, the assembled
     * {@link LlmClientResolver} is automatically wrapped with {@link RetryingLlmClientResolver}
     * using this policy.
     *
     * <p>Has no effect if {@link #withLlmClientResolver(LlmClientResolver)} was also
     * called — an explicit resolver is never wrapped automatically.
     *
     * <p>When {@code maxAttempts <= 1} the policy is stored but no wrapping occurs
     * (one attempt means no retry).
     *
     * @param llmRetryPolicy retry policy; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmRetryPolicy(LlmRetryPolicy llmRetryPolicy) {
      this.llmRetryPolicy = Validate.notNull(llmRetryPolicy,
          "LLM retry policy must not be null — use LlmRetryPolicy with maxAttempts >= 1");
      return this;
    }

    /**
     * Overrides the agent input context renderer.
     *
     * @param contextRenderer renderer instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withContextRenderer(ContextRenderer contextRenderer) {
      this.contextRenderer = Validate.notNull(contextRenderer, "contextRenderer must not be null");
      return this;
    }

    /**
     * Overrides the LLM command response parser.
     *
     * @param llmCommandParser parser instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmCommandParser(LlmCommandParser llmCommandParser) {
      this.llmCommandParser = Validate.notNull(llmCommandParser,
          "llmCommandParser must not be null");
      return this;
    }

    /**
     * Overrides the shared workflow event recorder.
     *
     * @param eventRecorder recorder instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withEventRecorder(EventRecorder eventRecorder) {
      this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
      return this;
    }

    /**
     * Overrides where {@link com.agentforge4j.core.command.CreateFileCommand} content is written.
     *
     * @param fileSink file sink instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withFileSink(FileSink fileSink) {
      this.fileSink = Validate.notNull(fileSink, "fileSink must not be null");
      return this;
    }

    /**
     * Overrides where {@link com.agentforge4j.core.command.CreateFileCommand} content is written.
     *
     * @param fileSinkPath path used to create a
     *                     {@link com.agentforge4j.runtime.command.LocalFileSink} instance
     *
     * @return this builder
     */
    public Builder withFileSinkPath(Path fileSinkPath) {
      this.fileSinkPath = Validate.requireDirectory(fileSinkPath,
          "fileSinkPath must be a directory");
      return this;
    }

    /**
     * Overrides the strategy for selecting an LLM provider from agent preferences.
     *
     * @param strategy selection strategy; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmProviderSelectionStrategy(LlmProviderSelectionStrategy strategy) {
      this.llmProviderSelectionStrategy = Validate.notNull(strategy, "strategy must not be null");
      return this;
    }

    /**
     * Overrides the {@link AgentInvoker} used for agent and SPAR steps.
     *
     * @param agentInvoker invoker instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withAgentInvoker(AgentInvoker agentInvoker) {
      this.agentInvoker = Validate.notNull(agentInvoker, "agentInvoker must not be null");
      return this;
    }

    /**
     * Overrides the observer invoked after each completed LLM call.
     *
     * @param llmCallObserver observer instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmCallObserver(LlmCallObserver llmCallObserver) {
      this.llmCallObserver = Validate.notNull(llmCallObserver, "llmCallObserver must not be null");
      return this;
    }

    /**
     * Overrides the resolver that maps a capability tier to a concrete model per provider. When not
     * set, a {@link ConfigModelTierResolver} built from the shipped defaults merged with any
     * {@code agentforge4j.llm.model-tiers.<provider>.<tier>} overrides is used.
     *
     * @param modelTierResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withModelTierResolver(ModelTierResolver modelTierResolver) {
      this.modelTierResolver = Validate.notNull(modelTierResolver,
          "modelTierResolver must not be null");
      return this;
    }

    /**
     * Sets the maximum nested workflow depth forwarded to {@link WorkflowRuntimeBuilder}.
     *
     * @param maxNestingDepth maximum nesting depth (at least 1)
     *
     * @return this builder
     */
    public Builder withMaxNestingDepth(int maxNestingDepth) {
      this.maxNestingDepth = Validate.isGreaterThanZero(maxNestingDepth,
          "maxNestingDepth must not be null").intValue();
      return this;
    }

    /**
     * Opts in to loading agents from the given filesystem directory at build time.
     *
     * @param agentsDir agents root directory; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withAgentsDir(Path agentsDir) {
      Validate.notNull(agentsDir, "agentsDir");
      this.agentsDir = Validate.requireDirectory(agentsDir, "agentsDir must be a valid directory");
      return this;
    }

    /**
     * Opts in to loading workflows from the given filesystem directory at build time.
     *
     * @param workflowsDir workflows root directory; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withWorkflowsDir(Path workflowsDir) {
      Validate.notNull(workflowsDir, "workflowsDir");
      this.workflowsDir = Validate.requireDirectory(workflowsDir,
          "workflowsDir must be a valid directory");
      return this;
    }

    /**
     * Enables or disables loading shipped agents from the classpath.
     *
     * @param loadShippedAgents {@code true} to load shipped agents
     *
     * @return this builder
     */
    public Builder withLoadShippedAgents(boolean loadShippedAgents) {
      this.loadShippedAgents = loadShippedAgents;
      this.loadShippedAgentsSet = true;
      return this;
    }

    /**
     * Enables or disables loading shipped workflows from the classpath.
     *
     * @param loadShippedWorkflows {@code true} to load shipped workflows
     *
     * @return this builder
     */
    public Builder withLoadShippedWorkflows(boolean loadShippedWorkflows) {
      this.loadShippedWorkflows = loadShippedWorkflows;
      this.loadShippedWorkflowsSet = true;
      return this;
    }

    /**
     * Enables or disables prompt-layer caching on the default {@link AgentInvoker}.
     *
     * @param cacheEnabled {@code true} to enable prompt cache boundaries on LLM requests
     *
     * @return this builder
     */
    public Builder withCacheEnabled(boolean cacheEnabled) {
      this.cacheEnabled = cacheEnabled;
      this.cacheEnabledSet = true;
      return this;
    }

    /**
     * Adds or replaces programmatic LLM provider configuration. Additive across provider keys;
     * last-write-wins within the same provider key.
     *
     * @param config provider configuration; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmProvider(LlmProviderConfig config) {
      Validate.notNull(config, "config must not be null");
      this.llmProviders.put(config.provider(), config);
      return this;
    }

    /**
     * Builds the {@link AgentForge4j} facade, assembling all components with defaults for any
     * overrides not set.
     *
     * @return immutable facade; never {@code null}
     *
     * @throws IllegalStateException if assembly fails (e.g. loading throws)
     */
    public AgentForge4j build() {
      Map<String, String> config = ConfigReader.read();
      applyConfig(config);

      Clock resolvedClock = (clock != null) ? clock : Clock.systemUTC();
      ObjectMapper resolvedMapper = (objectMapper != null)
          ? objectMapper : ConfigurationLoader.defaultObjectMapper();

      LoadedConfiguration loadedConfiguration = ConfigurationLoader.load(
          resolvedMapper, agentsDir, workflowsDir, loadShippedAgents, loadShippedWorkflows);

      AgentRepository resolvedAgentRepo = (agentRepository != null)
          ? agentRepository : ComponentDefaults.agentRepository(loadedConfiguration);

      WorkflowRepository resolvedWorkflowRepo = (workflowRepository != null)
          ? workflowRepository : ComponentDefaults.workflowRepository(loadedConfiguration);

      WorkflowStateRepository resolvedStateRepo = (workflowStateRepository != null)
          ? workflowStateRepository : new InMemoryWorkflowStateRepository();

      WorkflowEventLog resolvedEventLog = (workflowEventLog != null)
          ? workflowEventLog : new InMemoryWorkflowEventLog();

      IntegrationRegistry resolvedRegistry = (integrationRegistry != null)
          ? integrationRegistry : NoOpIntegrationRegistry.INSTANCE;

      FileSink resolvedFileSink = (fileSink != null)
          ? fileSink : ComponentDefaults.fileSink(fileSinkPath);

      List<LlmClient> llmClients = List.of();
      if (llmClientResolver == null) {
        llmClients = LlmClientWiring.buildLlmClients(resolvedMapper, llmProviders);
      }

      LlmClientResolver resolvedResolver = (llmClientResolver != null)
          ? llmClientResolver : new DefaultLlmClientResolver(llmClients);

      resolvedResolver = RuntimeAssembler.applyRetryPolicy(
          resolvedResolver, llmRetryPolicy, llmClientResolver != null);

      RuntimeAssembler.warnIfNoProviders(llmClients, llmClientResolver != null);

      LlmProviderSelectionStrategy resolvedStrategy = (llmProviderSelectionStrategy != null)
          ? llmProviderSelectionStrategy : new FirstAvailableProviderSelectionStrategy();

      ContextRenderer resolvedRenderer = (contextRenderer != null)
          ? contextRenderer : new ContextRenderer(resolvedMapper);

      LlmCommandParser resolvedParser = (llmCommandParser != null)
          ? llmCommandParser : new LlmCommandParser(resolvedMapper);

      EventRecorder resolvedRecorder = (eventRecorder != null)
          ? eventRecorder : new EventRecorder(resolvedEventLog, resolvedClock);

      LlmCallObserver resolvedObserver = (llmCallObserver != null)
          ? llmCallObserver : new LlmCallObserver(resolvedRecorder, resolvedMapper);

      ModelTierResolver resolvedTierResolver = (modelTierResolver != null)
          ? modelTierResolver
          : ConfigModelTierResolver.withShippedDefaultsAndOverrides(
              parseModelTierOverrides(config));

      AgentInvoker resolvedInvoker = RuntimeAssembler.agentInvoker(
          resolvedAgentRepo, resolvedResolver, resolvedRenderer, resolvedParser,
          resolvedMapper, resolvedRecorder, resolvedStrategy, cacheEnabled,
          resolvedObserver, resolvedTierResolver, agentInvoker, cacheEnabledSet);

      WorkflowRuntime resolvedRuntime = RuntimeAssembler.runtime(
          resolvedWorkflowRepo, resolvedStateRepo, resolvedEventLog, resolvedClock,
          resolvedFileSink, resolvedRegistry, resolvedInvoker, resolvedRecorder,
          maxNestingDepth);

      BootstrapComponents components = new BootstrapComponents(
          resolvedAgentRepo, resolvedWorkflowRepo, resolvedStateRepo, resolvedEventLog,
          resolvedResolver, resolvedRenderer, resolvedParser, resolvedRecorder,
          resolvedFileSink, resolvedStrategy, resolvedRegistry, resolvedMapper,
          resolvedClock, resolvedInvoker, resolvedObserver, loadedConfiguration);

      return new AgentForge4j(resolvedRuntime, loadedConfiguration, components);
    }

    /**
     * Applies non-LLM environment / system-property values as defaults for fields not already set
     * programmatically. Programmatic {@code with*} calls always win.
     *
     * @param config merged env/sys-prop map from {@link ConfigReader#read()}
     */
    private void applyConfig(Map<String, String> config) {
      if (agentsDir == null) {
        String val = config.get("agentforge4j.agents.path");
        if (val != null) {
          withAgentsDir(Path.of(val));
        }
      }
      if (workflowsDir == null) {
        String val = config.get("agentforge4j.workflows.path");
        if (val != null) {
          withWorkflowsDir(Path.of(val));
        }
      }
      if (fileSinkPath == null) {
        String val = config.get("agentforge4j.filesink.path");
        if (val != null) {
          withFileSinkPath(Path.of(val));
        }
      }
      if (!cacheEnabledSet) {
        String val = config.get("agentforge4j.llm.cache.enabled");
        if (val != null) {
          withCacheEnabled(Boolean.parseBoolean(val));
        }
      }
      if (maxNestingDepth == null) {
        String val = config.get("agentforge4j.max-nesting-depth");
        if (val != null) {
          try {
            withMaxNestingDepth(Integer.parseInt(val));
          } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid value for agentforge4j.max-nesting-depth: '%s' — expected an integer."
                    .formatted(val),
                exception);
          }
        }
      }
      if (!loadShippedAgentsSet) {
        String val = config.get("agentforge4j.load-shipped-agents");
        if (val != null) {
          withLoadShippedAgents(Boolean.parseBoolean(val));
        }
      }
      if (!loadShippedWorkflowsSet) {
        String val = config.get("agentforge4j.load-shipped-workflows");
        if (val != null) {
          withLoadShippedWorkflows(Boolean.parseBoolean(val));
        }
      }
    }

    /**
     * Parses {@code agentforge4j.llm.model-tiers.<provider>.<tier>=<model>} entries from the merged
     * configuration into a provider→tier→model override map. The tier name is case-insensitive and
     * must match a {@link ModelTier} constant. Provider names may contain dashes (e.g.
     * {@code azure-openai}); the tier is the segment after the final dot.
     *
     * @param config merged env/sys-prop map from {@link ConfigReader#read()}
     *
     * @return parsed overrides; empty when none are configured
     *
     * @throws IllegalStateException if a key is malformed or names an unknown tier
     */
    private Map<String, Map<ModelTier, String>> parseModelTierOverrides(
        Map<String, String> config) {
      String prefix = "agentforge4j.llm.model-tiers.";
      Map<String, Map<ModelTier, String>> overrides = new HashMap<>();
      config.entrySet().stream()
          .filter(en -> en.getKey().startsWith(prefix))
          .forEach(en -> addProviderTier(en, prefix, overrides));
      return overrides;
    }

    private static void addProviderTier(Entry<String, String> entry, String prefix,
        Map<String, Map<ModelTier, String>> overrides) {
      String key = entry.getKey();
      String remainder = key.substring(prefix.length());
      int lastDot = getProviderEndIndex(remainder, key);
      String provider = remainder.substring(0, lastDot);
      String tierName = remainder.substring(lastDot + 1);
      ModelTier tier = getModelTier(tierName, key);
      overrides.computeIfAbsent(provider, providerKey -> new EnumMap<>(ModelTier.class))
          .put(tier, entry.getValue());
    }

    private static int getProviderEndIndex(String remainder, String key) {
      int lastDot = remainder.lastIndexOf('.');
      if (lastDot <= 0 || lastDot == remainder.length() - 1) {
        throw new IllegalStateException(
            ("Invalid model tier config key '%s' — expected "
                + "agentforge4j.llm.model-tiers.<provider>.<tier>").formatted(key));
      }
      return lastDot;
    }

    private static ModelTier getModelTier(String tierName, String key) {
      try {
        return ModelTier.valueOf(tierName.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
        throw new IllegalStateException(
            ("Invalid tier '%s' in '%s' — valid tiers: LITE, STANDARD, POWERFUL")
                .formatted(tierName, key),
            exception);
      }
    }
  }
}
