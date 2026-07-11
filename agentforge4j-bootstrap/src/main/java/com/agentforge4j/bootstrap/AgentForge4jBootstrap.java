// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.contextpack.FileSystemContextPackLoader;
import com.agentforge4j.config.loader.integration.FileSystemIntegrationConfigLoader;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.integration.EnvironmentSecretResolver;
import com.agentforge4j.core.spi.integration.IntegrationConfigLoader;
import com.agentforge4j.core.spi.integration.IntegrationRepository;
import com.agentforge4j.core.spi.integration.MutableIntegrationRepository;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.ToolCatalog;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.llm.ConfigModelTierResolver;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.WorkflowRuntimeBuilder;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.agentforge4j.runtime.tool.DefaultToolCatalog;
import com.agentforge4j.runtime.tool.DefaultToolExecutionService;
import com.agentforge4j.runtime.tool.InMemoryIntegrationRepository;
import com.agentforge4j.runtime.tool.InMemoryPendingToolInvocationStore;
import com.agentforge4j.runtime.tool.IntegrationToolProviderResolver;
import com.agentforge4j.runtime.tool.SecureDefaultToolPolicy;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.net.HttpEgressGuard;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Static entry point for assembling an {@link AgentForge4j} facade with framework-agnostic defaults.
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
   * Returns a new {@link Builder} pre-populated with all framework defaults. No arguments are required; the returned
   * builder produces a fully functional {@link AgentForge4j} instance when {@link Builder#build()} is called without
   * any overrides.
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
   * and last-write-wins within a provider key. All other {@code with*} methods are last-write-wins.
   */
  public static final class Builder {

    private Clock clock;
    private ObjectMapper objectMapper;
    private AgentRepository agentRepository;
    private WorkflowRepository workflowRepository;
    private WorkflowStateRepository workflowStateRepository;
    private WorkflowEventLog workflowEventLog;
    private LlmClientResolver llmClientResolver;
    private LlmRetryPolicy llmRetryPolicy;
    private ContextRenderer contextRenderer;
    private LlmCommandParser llmCommandParser;
    private EventRecorder eventRecorder;
    private FileSink fileSink;
    private LlmProviderSelectionStrategy llmProviderSelectionStrategy;
    private AgentInvoker agentInvoker;
    private LlmCallObserver llmCallObserver;
    private RunExecutionInterceptor runExecutionInterceptor;
    private ModelTierResolver modelTierResolver;
    private RequirementResolver requirementResolver;
    private List<ArtifactValidator> artifactValidators = List.of();
    private List<ToolProvider> toolProviders = List.of();
    private ToolProviderResolver toolProviderResolver;
    private ToolPolicy toolPolicy;
    private PendingToolInvocationStore pendingToolInvocationStore;
    private ToolExecutionOptions toolExecutionOptions;
    private boolean allowPrivateNetworks;
    private Path integrationsDir;
    private Path contextPacksDir;
    private IntegrationConfigLoader integrationConfigLoader;
    private MutableIntegrationRepository integrationRepository;
    private ToolProviderFactory toolProviderFactory;
    private SecretResolver secretResolver;
    private LlmSecretResolver llmSecretResolver;

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
      this.workflowRepository = Validate.notNull(workflowRepository, "workflowRepository must not be null");
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
      this.workflowEventLog = Validate.notNull(workflowEventLog, "workflowEventLog must not be null");
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
      this.llmClientResolver = Validate.notNull(llmClientResolver, "llmClientResolver must not be null");
      return this;
    }

    /**
     * Configures the LLM retry policy. When {@code maxAttempts > 1}, the assembled {@link LlmClientResolver} is
     * automatically wrapped with {@link RetryingLlmClientResolver} using this policy.
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
      this.llmCommandParser = Validate.notNull(llmCommandParser, "llmCommandParser must not be null");
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
     * @param fileSinkPath path used to create a {@link com.agentforge4j.runtime.command.LocalFileSink} instance
     *
     * @return this builder
     */
    public Builder withFileSinkPath(Path fileSinkPath) {
      this.fileSinkPath = Validate.requireDirectory(fileSinkPath, "fileSinkPath must be a directory");
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
     * Registers a control interceptor fired before a run enters main execution and before each LLM call. Either hook
     * may throw {@link com.agentforge4j.runtime.interceptor.ExecutionBlockedException} to block. Defaults to a no-op,
     * so behaviour is unchanged when not set.
     *
     * @param runExecutionInterceptor interceptor instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withRunExecutionInterceptor(RunExecutionInterceptor runExecutionInterceptor) {
      this.runExecutionInterceptor = Validate.notNull(runExecutionInterceptor,
          "runExecutionInterceptor must not be null");
      return this;
    }

    /**
     * Overrides the resolver that maps a capability tier to a concrete model per provider. When not set, a
     * {@link ConfigModelTierResolver} built from the shipped defaults merged with any
     * {@code agentforge4j.llm.model-tiers.<provider>.<tier>} overrides is used.
     *
     * @param modelTierResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withModelTierResolver(ModelTierResolver modelTierResolver) {
      this.modelTierResolver = Validate.notNull(modelTierResolver, "modelTierResolver must not be null");
      return this;
    }

    /**
     * Overrides the resolver consulted for declared workflow requirements at the run-start checkpoint and at deferred
     * first use. When not set, the runtime defaults to the in-process {@code DefaultRequirementResolver}
     * (default-or-empty), preserving the fail-fast guarantee out of the box. The embedding application supplies a
     * store-backed resolver here.
     *
     * @param requirementResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withRequirementResolver(RequirementResolver requirementResolver) {
      this.requirementResolver = Validate.notNull(requirementResolver, "requirementResolver must not be null");
      return this;
    }

    /**
     * Registers additional {@link ArtifactValidator}s a {@code VALIDATE} step may select by id. These are appended to
     * the built-in {@code agent-bundle} validator (which is always present so shipped agent-bundle workflows keep
     * working); a supplied validator that reuses an already-registered id fails fast at runtime assembly.
     *
     * @param artifactValidators validators to add; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withArtifactValidators(List<ArtifactValidator> artifactValidators) {
      this.artifactValidators =
          List.copyOf(Validate.notNull(artifactValidators, "artifactValidators must not be null"));
      return this;
    }

    /**
     * Provides pre-built tool providers (for example MCP servers) to expose to the runtime. Configuring any tool
     * support enables the tool-execution chokepoint and registers the {@code TOOL_INVOCATION} handler; with none
     * configured, tool invocation is unavailable and behaviour is unchanged.
     *
     * <p>These providers are merged with any configured integrations source into the single
     * {@link IntegrationToolProviderResolver}; the two coexist unless they expose the same capability, which fails
     * fast. Has no effect if {@link #withToolProviderResolver(ToolProviderResolver)} is set — an explicit resolver is
     * the sole resolver.
     *
     * <p><strong>Trust boundary.</strong> Providers supplied here are trusted embedder code: the
     * {@link com.agentforge4j.core.spi.tool.ToolSourceKind} their descriptors declare is taken at face value by the
     * secure default {@link com.agentforge4j.core.spi.tool.ToolPolicy}, so a provider that declares an in-process
     * source is allowed by default. Supply a custom {@code ToolPolicy} to gate providers whose declared kind you do
     * not control.
     *
     * @param toolProviders providers to expose; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withToolProviders(List<ToolProvider> toolProviders) {
      this.toolProviders = List.copyOf(Validate.notNull(toolProviders, "toolProviders must not be null"));
      return this;
    }

    /**
     * Overrides the capability resolver (for example a binding-aware resolver supplied by the embedding application).
     * When set, it is the sole resolver and {@link #withToolProviders(List)} is not used to build one.
     *
     * @param toolProviderResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withToolProviderResolver(ToolProviderResolver toolProviderResolver) {
      this.toolProviderResolver = Validate.notNull(toolProviderResolver, "toolProviderResolver must not be null");
      return this;
    }

    /**
     * Overrides the tool policy. Defaults to the secure-by-default policy ({@code SecureDefaultToolPolicy}), which
     * denies remote-network and local-process tools unless an explicit policy ({@code ToolPolicy.allowAll()} or a
     * custom one) opts in.
     *
     * @param toolPolicy policy instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withToolPolicy(ToolPolicy toolPolicy) {
      this.toolPolicy = Validate.notNull(toolPolicy, "toolPolicy must not be null");
      return this;
    }

    /**
     * Overrides the pending tool invocation store. Defaults to an in-memory store.
     *
     * @param pendingToolInvocationStore store instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withPendingToolInvocationStore(PendingToolInvocationStore pendingToolInvocationStore) {
      this.pendingToolInvocationStore =
          Validate.notNull(pendingToolInvocationStore, "pendingToolInvocationStore must not be null");
      return this;
    }

    /**
     * Overrides the tool execution options (authoritative timeout, retries, backoff). Defaults to
     * {@link ToolExecutionOptions#defaults()}.
     *
     * @param toolExecutionOptions options instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withToolExecutionOptions(ToolExecutionOptions toolExecutionOptions) {
      this.toolExecutionOptions = Validate.notNull(toolExecutionOptions, "toolExecutionOptions must not be null");
      return this;
    }

    /**
     * Lifts the outbound-egress guard's private/loopback/link-local/cloud-metadata blocks for HTTP and MCP
     * streamable-HTTP tools. <strong>Development only</strong> — enabling this disables the cloud-metadata-IP (SSRF)
     * protection. The {@code http}/{@code https} scheme allowlist still applies. Defaults to {@code false}
     * (fail-closed).
     *
     * @param allowPrivateNetworks whether to allow private/loopback network targets
     *
     * @return this builder
     */
    public Builder withAllowPrivateNetworks(boolean allowPrivateNetworks) {
      this.allowPrivateNetworks = allowPrivateNetworks;
      return this;
    }

    /**
     * Opts in to loading integration definitions from top-level {@code *.json} files in the given directory at build
     * time. Loaded definitions are saved into the integration repository and resolved into tool providers through the
     * discovered {@code IntegrationToolProviderFactory} contributions, enabling tool support.
     *
     * <p>Has no effect if {@link #withToolProviderResolver(ToolProviderResolver)} is also set —
     * an explicit resolver is the sole resolver. Coexists with {@link #withToolProviders(List)}: the two sources are
     * merged into one resolver and only a shared capability fails fast.
     *
     * @param integrationsDir integrations root directory; must be an existing directory
     *
     * @return this builder
     */
    public Builder withIntegrationsDir(Path integrationsDir) {
      Validate.notNull(integrationsDir, "integrationsDir");
      this.integrationsDir = Validate.requireDirectory(integrationsDir, "integrationsDir must be a valid directory");
      return this;
    }

    /**
     * Opts in to loading context packs from the given filesystem directory at build time, laid out
     * as {@code <contextPacksDir>/<name>/pack.json} plus each variant's content file (see
     * {@link FileSystemContextPackLoader}). Loaded packs both satisfy {@code CONTEXT_PACK} selector
     * validation at load time and are resolvable by the runtime at invocation time — without this,
     * every {@code CONTEXT_PACK} selector fails validation, since the loaded pack set defaults to
     * empty.
     *
     * @param contextPacksDir context packs root directory; must be an existing directory
     *
     * @return this builder
     */
    public Builder withContextPacksDir(Path contextPacksDir) {
      Validate.notNull(contextPacksDir, "contextPacksDir");
      this.contextPacksDir = Validate.requireDirectory(contextPacksDir,
          "contextPacksDir must be a valid directory");
      return this;
    }

    /**
     * Overrides the integration definition loader. When set, it is used instead of the filesystem loader over
     * {@link #withIntegrationsDir(Path)}.
     *
     * @param integrationConfigLoader loader instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withIntegrationConfigLoader(IntegrationConfigLoader integrationConfigLoader) {
      this.integrationConfigLoader = Validate.notNull(integrationConfigLoader,
          "integrationConfigLoader must not be null");
      return this;
    }

    /**
     * Overrides the integration repository that loaded definitions are saved into and the capability resolver reads
     * from. Defaults to an in-memory repository.
     *
     * @param integrationRepository repository instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withIntegrationRepository(MutableIntegrationRepository integrationRepository) {
      this.integrationRepository = Validate.notNull(integrationRepository, "integrationRepository must not be null");
      return this;
    }

    /**
     * Overrides the factory that realises integration definitions as tool providers. Defaults to the
     * {@link ServiceLoaderToolProviderFactory} aggregating the discovered per-type contributions.
     *
     * @param toolProviderFactory factory instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withToolProviderFactory(ToolProviderFactory toolProviderFactory) {
      this.toolProviderFactory = Validate.notNull(toolProviderFactory, "toolProviderFactory must not be null");
      return this;
    }

    /**
     * Overrides the resolver that turns a secret-reference key into its live value at invoke time (for example an HTTP
     * integration's {@code secretHeaders}). Defaults to {@link EnvironmentSecretResolver}, which reads process
     * environment variables then system properties. An embedding application supplies its own implementation backed by
     * its secret store.
     *
     * @param secretResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withSecretResolver(SecretResolver secretResolver) {
      this.secretResolver = Validate.notNull(secretResolver, "secretResolver must not be null");
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
      this.maxNestingDepth = Validate.isGreaterThanZero(maxNestingDepth, "maxNestingDepth must not be null").intValue();
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
      this.workflowsDir = Validate.requireDirectory(workflowsDir, "workflowsDir must be a valid directory");
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
     * Adds or replaces programmatic LLM provider configuration. Additive across provider keys; last-write-wins within
     * the same provider key.
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
     * Overrides the {@link LlmSecretResolver} used to resolve provider credential references. The default
     * ({@link EnvSystemPropertyLlmSecretResolver}) passes literal credentials through and resolves
     * {@code env:}/{@code sysprop:} references from the process environment / system properties.
     *
     * @param llmSecretResolver resolver instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder withLlmSecretResolver(LlmSecretResolver llmSecretResolver) {
      this.llmSecretResolver = Validate.notNull(llmSecretResolver, "llmSecretResolver must not be null");
      return this;
    }

    /**
     * Builds the {@link AgentForge4j} facade, assembling all components with defaults for any overrides not set.
     *
     * @return immutable facade; never {@code null}
     *
     * @throws IllegalStateException if assembly fails (e.g. loading throws)
     */
    public AgentForge4j build() {
      Map<String, String> config = ConfigReader.read();
      applyConfig(config);

      Clock resolvedClock = ObjectUtils.getIfNull(clock, Clock::systemUTC);
      ObjectMapper resolvedMapper = ObjectUtils.getIfNull(objectMapper, ConfigurationLoader::defaultObjectMapper);

      // Context packs: loaded once, up front, so the same set both satisfies CONTEXT_PACK selector
      // validation (via loadedPacksByName below) and is resolvable by the runtime (via the
      // ContextPackRegistry built from the same list, passed to RuntimeAssembler.runtime further
      // down). Empty (no packs configured) when withContextPacksDir was never called — matching the
      // runtime's own ContextPackRegistry.EMPTY default, so behaviour is unchanged when unset.
      List<ContextPack> loadedContextPacks = loadContextPacks(resolvedMapper);
      Map<String, ContextPack> loadedPacksByName = loadedContextPacks.stream()
          .collect(Collectors.toMap(ContextPack::name, pack -> pack));

      LoadedConfiguration loadedConfiguration = ConfigurationLoader.load(
          resolvedMapper, agentsDir, workflowsDir, loadShippedAgents, loadShippedWorkflows,
          loadedPacksByName);

      AgentRepository resolvedAgentRepo = ObjectUtils.getIfNull(agentRepository,
          () -> ComponentDefaults.agentRepository(loadedConfiguration));

      WorkflowRepository resolvedWorkflowRepo = ObjectUtils.getIfNull(workflowRepository,
          () -> ComponentDefaults.workflowRepository(loadedConfiguration));

      WorkflowStateRepository resolvedStateRepo = ObjectUtils.getIfNull(workflowStateRepository,
          InMemoryWorkflowStateRepository::new);

      WorkflowEventLog resolvedEventLog = ObjectUtils.getIfNull(workflowEventLog,
          InMemoryWorkflowEventLog::new);

      FileSink resolvedFileSink = ObjectUtils.getIfNull(fileSink,
          () -> ComponentDefaults.fileSink(fileSinkPath));

      LlmSecretResolver resolvedLlmSecretResolver = ObjectUtils.getIfNull(llmSecretResolver,
          EnvSystemPropertyLlmSecretResolver::new);
      List<LlmClient> llmClients =
          (llmClientResolver == null) ? LlmClientWiring.buildLlmClients(resolvedMapper,
              llmProviders, resolvedLlmSecretResolver) :
              List.of();

      LlmClientResolver resolvedResolver = ObjectUtils.getIfNull(llmClientResolver,
          () -> new DefaultLlmClientResolver(llmClients));

      resolvedResolver = RuntimeAssembler.applyRetryPolicy(
          resolvedResolver, llmRetryPolicy, llmClientResolver != null);

      RuntimeAssembler.warnIfNoProviders(llmClients, llmClientResolver != null);

      LlmProviderSelectionStrategy resolvedStrategy = ObjectUtils.getIfNull(
          llmProviderSelectionStrategy,
          FirstAvailableProviderSelectionStrategy::new);

      ContextRenderer resolvedRenderer = ObjectUtils.getIfNull(contextRenderer,
          () -> new ContextRenderer(resolvedMapper));

      LlmCommandParser resolvedParser = ObjectUtils.getIfNull(llmCommandParser,
          () -> new LlmCommandParser(resolvedMapper));

      EventRecorder resolvedRecorder = ObjectUtils.getIfNull(eventRecorder,
          () -> new EventRecorder(resolvedEventLog, resolvedClock));

      LlmCallObserver resolvedObserver = ObjectUtils.getIfNull(llmCallObserver,
          () -> new LlmCallObserver(resolvedRecorder, resolvedMapper));

      RunExecutionInterceptor resolvedInterceptor = ObjectUtils.getIfNull(runExecutionInterceptor,
          () -> RunExecutionInterceptor.NO_OP);

      ModelTierResolver resolvedTierResolver = ObjectUtils.getIfNull(modelTierResolver,
          () -> ConfigModelTierResolver.withShippedDefaultsAndOverrides(
              parseModelTierOverrides(config)));

      // Tool support is opt-in: assembled only when integrations, providers, or a resolver are
      // configured, so the OSS default (no MCP) is byte-identical to prior behaviour.
      ToolCatalog resolvedToolCatalog = null;
      ToolExecutionService resolvedToolExecutionService = null;
      PendingToolInvocationStore resolvedPendingStore = null;
      ToolSupport toolSupport = resolveToolSupport(resolvedMapper);
      ToolProviderResolver resolver = toolSupport.resolver();
      if (resolver != null) {
        resolvedToolCatalog = new DefaultToolCatalog(resolver);
        resolvedPendingStore = ObjectUtils.getIfNull(pendingToolInvocationStore,
            InMemoryPendingToolInvocationStore::new);
        resolvedToolExecutionService = getResolvedToolExecutionService(resolver,
            resolvedPendingStore, resolvedRecorder, resolvedMapper, resolvedClock);
      }

      AgentInvoker resolvedInvoker = RuntimeAssembler.agentInvoker(
          resolvedAgentRepo, resolvedResolver, resolvedRenderer, resolvedParser,
          resolvedMapper, resolvedRecorder, resolvedStrategy, cacheEnabled,
          resolvedObserver, resolvedTierResolver, agentInvoker, cacheEnabledSet,
          resolvedToolCatalog, resolvedInterceptor);

      WorkflowRuntime resolvedRuntime = RuntimeAssembler.runtime(
          resolvedWorkflowRepo, resolvedStateRepo, resolvedEventLog, resolvedClock,
          resolvedFileSink, resolvedInvoker, resolvedRecorder,
          maxNestingDepth, resolvedToolExecutionService, resolvedPendingStore,
          requirementResolver, resolvedInterceptor, resolvedMapper, artifactValidators,
          ContextPackRegistry.of(loadedContextPacks));

      BootstrapComponents components = new BootstrapComponents(resolvedAgentRepo, resolvedWorkflowRepo,
          resolvedStateRepo, resolvedEventLog, resolvedResolver, resolvedRenderer, resolvedParser, resolvedRecorder,
          resolvedFileSink, resolvedStrategy, toolSupport.integrationRepository(), resolver,
          resolvedToolExecutionService, resolvedMapper,
          resolvedClock, resolvedInvoker, resolvedObserver, loadedConfiguration);

      return new AgentForge4j(resolvedRuntime, loadedConfiguration, components);
    }

    /**
     * Resolves tool support from the configured tool sources; both parts are {@code null} when no tool support is
     * configured. An explicit {@link #withToolProviderResolver(ToolProviderResolver)} is the sole resolver. Otherwise a
     * single {@link IntegrationToolProviderResolver} merges two sources into one capability index: the active
     * definitions of a configured integrations source (directory, loader, or repository) and the pre-built providers
     * from {@link #withToolProviders(List)}. The two coexist; the only failure is a per-capability collision across the
     * union, rejected by the resolver. The integration repository is returned (for component exposure) only when an
     * integrations source was configured.
     *
     * @param resolvedMapper the resolved Jackson mapper for the filesystem integration loader
     *
     * @return the resolver driving tool support plus the integration repository feeding it (the repository only on the
     * integrations path)
     */
    /**
     * Loads context packs from {@link #contextPacksDir} when configured, using the same schema
     * provider convention as {@link #resolveAndSaveIntegrations}.
     *
     * @param resolvedMapper the resolved Jackson mapper for the filesystem context-pack loader
     *
     * @return the loaded packs; empty when {@code withContextPacksDir} was never called
     */
    private List<ContextPack> loadContextPacks(ObjectMapper resolvedMapper) {
      if (contextPacksDir == null) {
        return List.of();
      }
      FileSystemContextPackLoader loader = new FileSystemContextPackLoader(resolvedMapper,
          new ClasspathSchemaProvider(), contextPacksDir);
      return loader.load();
    }

    private ToolSupport resolveToolSupport(ObjectMapper resolvedMapper) {
      if (toolProviderResolver != null) {
        return new ToolSupport(toolProviderResolver, null);
      }
      boolean integrationsConfigured = integrationsDir != null || integrationConfigLoader != null
          || integrationRepository != null;
      if (!integrationsConfigured && toolProviders.isEmpty()) {
        return new ToolSupport(null, null);
      }
      MutableIntegrationRepository resolvedIntegrationRepository = ObjectUtils.getIfNull(
          integrationRepository, InMemoryIntegrationRepository::new);
      resolveAndSaveIntegrations(resolvedMapper, resolvedIntegrationRepository);
      return new ToolSupport(resolveToolProviderResolver(
          resolvedMapper, resolvedIntegrationRepository, integrationsConfigured),
          integrationsConfigured ? resolvedIntegrationRepository : null);
    }

    private void resolveAndSaveIntegrations(ObjectMapper resolvedMapper,
        MutableIntegrationRepository resolvedIntegrationRepository) {
      IntegrationConfigLoader resolvedIntegrationLoader = integrationConfigLoader;
      if (resolvedIntegrationLoader == null && integrationsDir != null) {
        resolvedIntegrationLoader = new FileSystemIntegrationConfigLoader(
            resolvedMapper, new ClasspathSchemaProvider(), integrationsDir);
      }
      if (resolvedIntegrationLoader != null) {
        resolvedIntegrationLoader.load().forEach(resolvedIntegrationRepository::save);
      }
    }

    private ToolProviderResolver resolveToolProviderResolver(ObjectMapper resolvedMapper,
        MutableIntegrationRepository resolvedIntegrationRepository,
        boolean integrationsConfigured) {
      return new IntegrationToolProviderResolver(resolvedIntegrationRepository,
          resolveToolProviderFactory(resolvedMapper, integrationsConfigured), toolProviders);
    }

    /**
     * Resolves the factory that realises active integration definitions as tool providers. An explicitly configured
     * factory always wins. Otherwise the ServiceLoader-discovered aggregator is built only when an integrations source
     * is configured; on the pre-built-providers-only path the (empty) repository yields no definitions to realise, so
     * the factory is never invoked and the discovery scan is skipped.
     *
     * @param resolvedMapper         the shared Jackson mapper threaded into discovered contributions
     * @param integrationsConfigured whether an integrations source (dir, loader, or repository) was configured
     *
     * @return the resolved factory; never {@code null}
     */
    private ToolProviderFactory resolveToolProviderFactory(ObjectMapper resolvedMapper,
        boolean integrationsConfigured) {
      if (toolProviderFactory != null) {
        return toolProviderFactory;
      }
      if (!integrationsConfigured) {
        return definition -> {
          throw new IllegalStateException(
              "No ToolProviderFactory is configured and no integrations source is enabled; "
                  + "this factory must not be invoked");
        };
      }
      SecretResolver resolvedSecretResolver = ObjectUtils.getIfNull(secretResolver,
          EnvironmentSecretResolver::new);
      return ServiceLoaderToolProviderFactory.discover(resolvedMapper, resolvedSecretResolver,
          new HttpEgressGuard(allowPrivateNetworks));
    }

    /**
     * The resolved tool-support pair: the capability resolver driving tool execution and, on the integrations path
     * only, the {@link IntegrationRepository} feeding it. Both are {@code null} when no tool support is configured.
     */
    private record ToolSupport(ToolProviderResolver resolver,
                               IntegrationRepository integrationRepository) {

    }

    private ToolExecutionService getResolvedToolExecutionService(ToolProviderResolver resolver,
        PendingToolInvocationStore resolvedPendingStore, EventRecorder resolvedRecorder,
        ObjectMapper resolvedMapper, Clock resolvedClock) {
      ToolPolicy resolvedPolicy = ObjectUtils.getIfNull(toolPolicy, SecureDefaultToolPolicy::new);
      ToolExecutionOptions resolvedOptions = ObjectUtils.getIfNull(toolExecutionOptions,
          ToolExecutionOptions::defaults);
      return new DefaultToolExecutionService(
          resolver, resolvedPolicy, resolvedPendingStore, resolvedOptions,
          resolvedRecorder, resolvedMapper, resolvedClock);
    }

    /**
     * Applies non-LLM environment / system-property values as defaults for fields not already set programmatically.
     * Programmatic {@code with*} calls always win.
     *
     * @param config merged env/sys-prop map from {@link ConfigReader#read()}
     */
    private void applyConfig(Map<String, String> config) {
      applyConfigPath(agentsDir, config.get("agentforge4j.agents.path"), this::withAgentsDir);
      applyConfigPath(workflowsDir, config.get("agentforge4j.workflows.path"),
          this::withWorkflowsDir);
      applyConfigPath(integrationsDir, config.get("agentforge4j.integrations.dir"),
          this::withIntegrationsDir);
      applyConfigPath(contextPacksDir, config.get("agentforge4j.context-packs.dir"),
          this::withContextPacksDir);
      applyConfigPath(fileSinkPath, config.get("agentforge4j.filesink.path"),
          this::withFileSinkPath);

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

    private static void applyConfigPath(Path path, String configValue, Function<Path, Builder> with) {
      if (path == null && configValue != null) {
        with.apply(Path.of(configValue));
      }
    }

    /**
     * Parses {@code agentforge4j.llm.model-tiers.<provider>.<tier>=<model>} entries from the merged configuration into
     * a provider→tier→model override map. The tier name is case-insensitive and must match a {@link ModelTier}
     * constant. Provider names may contain dashes (e.g. {@code azure-openai}); the tier is the segment after the final
     * dot.
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
            ("Invalid tier '%s' in '%s' — valid tiers: %s")
                .formatted(tierName, key, ModelTier.joinedNames()), exception);
      }
    }
  }
}
