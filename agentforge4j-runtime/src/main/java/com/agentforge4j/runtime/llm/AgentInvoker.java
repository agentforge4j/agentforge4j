// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandResponseSchemaRenderer;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.command.schema.SystemRulesProvider;
import com.agentforge4j.core.spi.governance.TokenGovernanceSignal;
import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.spi.tool.ToolCatalog;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolutionException;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.interceptor.LlmCallContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.waste.WasteDetector;
import com.agentforge4j.runtime.waste.WasteDetectorHistoryStore;
import com.agentforge4j.runtime.waste.WasteDetectorInvocationHistory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Drives a single agent invocation: resolves the agent definition, picks the first enabled provider preference, renders
 * the input context as JSON, executes against the resolved {@link LlmClient}, and parses the structured command
 * output.
 */
public final class AgentInvoker {

  private static final System.Logger LOG = System.getLogger(AgentInvoker.class.getName());
  private static final int RETRY_ATTEMPTS = 2;

  /**
   * Default maximum characters recorded per {@link WorkflowEventType#LLM_OUTPUT} event.
   */
  public static final int DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP = 8000;

  private final AgentRepository agentRepository;
  private final LlmClientResolver llmClientResolver;
  private final LlmProviderSelectionStrategy llmProviderSelectionStrategy;
  private final ContextRenderer contextRenderer;
  private final LlmCommandParser llmCommandParser;
  private final ObjectMapper objectMapper;
  private final EventRecorder eventRecorder;
  private final int llmOutputEventCharCap;
  private final boolean promptCacheEnabled;
  private final LlmCallObserver llmCallObserver;
  private final ModelTierResolver modelTierResolver;
  private final ToolCatalog toolCatalog;
  private final RunExecutionInterceptor runExecutionInterceptor;
  private final WasteSignalPolicy wasteSignalPolicy;
  private final CommandResponseSchemaRenderer schemaRenderer = new CommandResponseSchemaRenderer();
  private final SystemRulesProvider systemRulesProvider = new SystemRulesProvider();

  /**
   * Command type an agent must opt into ({@code supportedCommands}) before tools are advertised to it.
   */
  private static final String TOOL_INVOCATION_COMMAND = "TOOL_INVOCATION";

  /**
   * Returns a new builder for {@link AgentInvoker}.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  private AgentInvoker(Builder builder) {
    this.agentRepository = builder.agentRepository;
    this.llmClientResolver = builder.llmClientResolver;
    this.llmProviderSelectionStrategy = builder.llmProviderSelectionStrategy;
    this.contextRenderer = builder.contextRenderer;
    this.llmCommandParser = builder.llmCommandParser;
    this.objectMapper = builder.objectMapper;
    this.eventRecorder = builder.eventRecorder;
    this.llmOutputEventCharCap = builder.llmOutputEventCharCap;
    this.promptCacheEnabled = builder.promptCacheEnabled;
    this.llmCallObserver = builder.llmCallObserver;
    this.modelTierResolver = builder.modelTierResolver;
    this.toolCatalog = builder.toolCatalog;
    this.runExecutionInterceptor = builder.runExecutionInterceptor;
    this.wasteSignalPolicy = builder.wasteSignalPolicy;
  }

  /**
   * Builder for {@link AgentInvoker}.
   */
  public static final class Builder {

    private AgentRepository agentRepository;
    private LlmClientResolver llmClientResolver;
    private ContextRenderer contextRenderer;
    private LlmCommandParser llmCommandParser;
    private ObjectMapper objectMapper;
    private EventRecorder eventRecorder;
    private LlmProviderSelectionStrategy llmProviderSelectionStrategy;
    private LlmCallObserver llmCallObserver;
    private ModelTierResolver modelTierResolver;
    private int llmOutputEventCharCap = DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP;
    private boolean promptCacheEnabled = false;
    private ToolCatalog toolCatalog;
    private RunExecutionInterceptor runExecutionInterceptor = RunExecutionInterceptor.NO_OP;
    private WasteSignalPolicy wasteSignalPolicy = WasteSignalPolicy.NO_OP;

    private Builder() {

    }

    /**
     * @param runExecutionInterceptor control interceptor fired before each LLM call; defaults to a no-op when not set
     *
     * @return this builder; never {@code null}
     */
    public Builder runExecutionInterceptor(RunExecutionInterceptor runExecutionInterceptor) {
      this.runExecutionInterceptor = Validate.notNull(runExecutionInterceptor,
          "runExecutionInterceptor must not be null");
      return this;
    }

    /**
     * @param agentRepository agent repository dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder agentRepository(AgentRepository agentRepository) {
      this.agentRepository = Validate.notNull(agentRepository, "Agent repository must not be null");
      return this;
    }

    /**
     * @param llmClientResolver LLM client resolver dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder llmClientResolver(LlmClientResolver llmClientResolver) {
      this.llmClientResolver = Validate.notNull(llmClientResolver,
          "LLM client resolver must not be null");
      return this;
    }

    /**
     * @param contextRenderer context renderer dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder contextRenderer(ContextRenderer contextRenderer) {
      this.contextRenderer = Validate.notNull(contextRenderer, "Context renderer must not be null");
      return this;
    }

    /**
     * @param llmCommandParser LLM command parser dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder llmCommandParser(LlmCommandParser llmCommandParser) {
      this.llmCommandParser = Validate.notNull(llmCommandParser,
          "LLM command parser must not be null");
      return this;
    }

    /**
     * @param objectMapper object mapper dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = Validate.notNull(objectMapper, "Object mapper must not be null");
      return this;
    }

    /**
     * @param eventRecorder event recorder dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder eventRecorder(EventRecorder eventRecorder) {
      this.eventRecorder = Validate.notNull(eventRecorder, "Event recorder must not be null");
      return this;
    }

    /**
     * @param llmProviderSelectionStrategy provider selection strategy dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder llmProviderSelectionStrategy(
        LlmProviderSelectionStrategy llmProviderSelectionStrategy) {
      this.llmProviderSelectionStrategy = Validate.notNull(llmProviderSelectionStrategy,
          "LLM provider selection strategy must not be null");
      return this;
    }

    /**
     * @param llmCallObserver call observer dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder llmCallObserver(LlmCallObserver llmCallObserver) {
      this.llmCallObserver = Validate.notNull(llmCallObserver,
          "LLM call observer must not be null");
      return this;
    }

    /**
     * @param modelTierResolver capability-tier to concrete-model resolver dependency
     *
     * @return this builder; never {@code null}
     */
    public Builder modelTierResolver(ModelTierResolver modelTierResolver) {
      this.modelTierResolver = Validate.notNull(modelTierResolver,
          "Model tier resolver must not be null");
      return this;
    }

    /**
     * @param llmOutputEventCharCap maximum output event characters (0 disables truncation)
     *
     * @return this builder; never {@code null}
     */
    public Builder llmOutputEventCharCap(int llmOutputEventCharCap) {
      this.llmOutputEventCharCap = Validate.isNotNegative(llmOutputEventCharCap,
          "LLM output event character cap must be zero or greater").intValue();
      return this;
    }

    /**
     * @param promptCacheEnabled whether prompt cache boundaries should be emitted
     *
     * @return this builder; never {@code null}
     */
    public Builder promptCacheEnabled(boolean promptCacheEnabled) {
      this.promptCacheEnabled = promptCacheEnabled;
      return this;
    }

    /**
     * Configures the optional, nullable {@link ToolCatalog} used to advertise tool capabilities to the LLM. When
     * {@code null} (the default) the prompt is unchanged and behaviour matches prior versions.
     *
     * @param toolCatalog tool catalog, or {@code null} to advertise no tools
     *
     * @return this builder; never {@code null}
     */
    public Builder toolCatalog(ToolCatalog toolCatalog) {
      this.toolCatalog = toolCatalog;
      return this;
    }

    /**
     * Overrides the policy reacting to {@link TokenGovernanceSignal}s raised by {@link WasteDetector}
     * for this invoker's calls. Defaults to {@link WasteSignalPolicy#NO_OP} — every raised signal is
     * still recorded as a {@code TOKEN_GOVERNANCE_SIGNAL} audit event regardless of this policy.
     *
     * @param wasteSignalPolicy policy instance; must not be {@code null}
     *
     * @return this builder
     */
    public Builder wasteSignalPolicy(WasteSignalPolicy wasteSignalPolicy) {
      this.wasteSignalPolicy = Validate.notNull(wasteSignalPolicy,
          "wasteSignalPolicy must not be null");
      return this;
    }

    /**
     * Builds the {@link AgentInvoker}.
     *
     * @return configured invoker; never {@code null}
     *
     * @throws IllegalArgumentException if any required field was not set
     */
    public AgentInvoker build() {
      Validate.notNull(agentRepository,
          "Agent repository must be set - call agentRepository(AgentRepository)");
      Validate.notNull(llmClientResolver,
          "LLM client resolver must be set - call llmClientResolver(LlmClientResolver)");
      Validate.notNull(contextRenderer,
          "Context renderer must be set - call contextRenderer(ContextRenderer)");
      Validate.notNull(llmCommandParser,
          "LLM command parser must be set - call llmCommandParser(LlmCommandParser)");
      Validate.notNull(objectMapper,
          "Object mapper must be set - call objectMapper(ObjectMapper)");
      Validate.notNull(eventRecorder,
          "Event recorder must be set - call eventRecorder(EventRecorder)");
      Validate.notNull(llmProviderSelectionStrategy,
          "LLM provider selection strategy must be set - "
              + "call llmProviderSelectionStrategy(LlmProviderSelectionStrategy)");
      Validate.notNull(llmCallObserver,
          "LLM call observer must be set - call llmCallObserver(LlmCallObserver)");
      Validate.notNull(modelTierResolver,
          "Model tier resolver must be set - call modelTierResolver(ModelTierResolver)");
      return new AgentInvoker(this);
    }
  }

  /**
   * Invokes an agent without a step-level tier override (equivalent to passing {@code null}).
   *
   * @param agentId        the agent to invoke; must not be blank
   * @param contextMapping context mapping for input rendering; must not be {@code null}
   * @param state          mutable run state; must not be {@code null}
   * @param stepPrompt     optional static step prompt material; may be blank
   *
   * @return the parsed invocation result; never {@code null}
   */
  public AgentInvocationResult invoke(String agentId,
      ContextMapping contextMapping,
      WorkflowState state,
      String stepPrompt) {
    return invoke(agentId, contextMapping, state, stepPrompt, null);
  }

  /**
   * Invokes an agent, optionally overriding the agent's capability tier for this step. Callers without an
   * active-workflow context use the run's root workflow id (from {@code state}) as the invocation identity's workflow
   * id; callers driving a nested workflow should use the {@code activeWorkflowId} overload so the identity reflects the
   * innermost active workflow.
   *
   * @param agentId        the agent to invoke; must not be blank
   * @param contextMapping context mapping for input rendering; must not be {@code null}
   * @param state          mutable run state; must not be {@code null}
   * @param stepPrompt     optional static step prompt material; may be blank
   * @param stepModelTier  optional step-level tier name overriding the agent tier; {@code null} or blank inherits the
   *                       agent tier
   *
   * @return the parsed invocation result; never {@code null}
   */
  public AgentInvocationResult invoke(String agentId,
      ContextMapping contextMapping,
      WorkflowState state,
      String stepPrompt,
      String stepModelTier) {
    Validate.notNull(state, "state must not be null");
    return invoke(agentId, contextMapping, state, stepPrompt, stepModelTier, state.getWorkflowId());
  }

  /**
   * Invokes an agent, carrying the innermost active workflow id onto the invocation identity. The run executes under a
   * single root run/state ({@code WorkflowState.workflowId} is the root and is immutable), so the active workflow id of
   * a nested workflow is not recoverable from {@code state} — the caller (which holds the execution context) supplies
   * it here so that {@link LlmInvocationIdentity#workflowId()} distinguishes steps of different nested workflows under
   * one run.
   *
   * @param agentId          the agent to invoke; must not be blank
   * @param contextMapping   context mapping for input rendering; must not be {@code null}
   * @param state            mutable run state; must not be {@code null}
   * @param stepPrompt       optional static step prompt material; may be blank
   * @param stepModelTier    optional step-level tier name overriding the agent tier; {@code null} or blank inherits the
   *                         agent tier
   * @param activeWorkflowId innermost active workflow id for this call (the run's root workflow id when not nested);
   *                         must not be blank
   *
   * @return the parsed invocation result; never {@code null}
   */
  public AgentInvocationResult invoke(String agentId,
      ContextMapping contextMapping,
      WorkflowState state,
      String stepPrompt,
      String stepModelTier,
      String activeWorkflowId) {
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(activeWorkflowId, "activeWorkflowId must not be blank");

    AgentDefinition agent = agentRepository.get(agentId);
    Validate.isTrue(agent.enabled(),
        "Agent '%s' is disabled and cannot be invoked".formatted(agent.id()));

    String userInput = contextRenderer.render(state.getContext(), contextMapping);
    return invokeWithAudit(agent, userInput, stepPrompt, stepModelTier, state, agentId,
        activeWorkflowId, contextMapping);
  }

  private AgentInvocationResult invokeWithAudit(AgentDefinition agent,
      String userInput,
      String stepPrompt,
      String stepModelTier,
      WorkflowState state,
      String actorIdForEvents,
      String activeWorkflowId,
      ContextMapping contextMapping) {
    ProviderPreference preference = llmProviderSelectionStrategy.selectInitialProvider(
        agent, llmClientResolver.listAvailableClients());
    ModelResolution resolution = resolveModel(agent, preference, stepModelTier);
    LOG.log(System.Logger.Level.DEBUG,
        "Agent invoker entry agentId={0}, provider={1}, model={2}, modelSource={3}",
        agent.id(), preference.provider(), resolution.resolvedModel(), resolution.modelSource());
    evaluateWasteSignals(state, agent, contextMapping, stepPrompt, resolution.requestedModelTier());
    LlmClient client = llmClientResolver.resolve(preference.provider());
    CommandResponseSchema schema = CommandSchemaFactory.build(agent.supportedCommands(),
        objectMapper);
    AssembledSystemPrompt assembled = appendToolCatalog(
        assembleSystemPrompt(agent, stepPrompt, schema), schema, state);

    // Control hook fired once per logical call (before the retry wrapper), matching the single post-call debit in
    // LlmCallObserver. maxOutputTokens is not set on the request today, so it is reported as null (the embedding
    // application falls back to its governing default); cached-input status is unknown pre-call. A registered
    // interceptor may throw ExecutionBlockedException here to veto the call.
    runExecutionInterceptor.beforeLlmCall(new LlmCallContext(
        state.getRunId(), state.getCurrentStepId(), agent.id(), preference.provider(),
        resolution.resolvedModel(), null, assembled.text().length() + userInput.length(), true));

    ParsedInvocation parsed = invokeLlmRecordAndParseWithRetry(
        agent, preference, resolution, client, assembled, userInput, schema, state,
        actorIdForEvents, activeWorkflowId);
    llmCallObserver.observe(actorIdForEvents, preference.provider(), parsed.llmResponse(),
        resolution.resolvedModel(), resolution.modelSource(), resolution.requestedModelTier(),
        state, parsed.attempt());
    return AgentInvocationResult.builder()
        .withRawResponse(parsed.llmResponse().text())
        .withCommands(parsed.commands())
        .withModelUsed(parsed.llmResponse().modelUsed())
        .withTokenUsage(parsed.llmResponse().tokenUsage())
        .withResolvedModel(resolution.resolvedModel())
        .withModelSource(resolution.modelSource())
        .withRequestedModelTier(resolution.requestedModelTier())
        .build();
  }

  /**
   * Evaluates {@link WasteDetector#evaluateDuplicateInvocation} and (when a tier was resolved)
   * {@link WasteDetector#evaluateUnjustifiedTierEscalation} against this step's persisted prior
   * invocation (see {@link WasteDetectorHistoryStore}), records any raised signal, then persists
   * this invocation as the new "prior" for next time.
   *
   * <p>{@code scopedContextFingerprint} fingerprints the mapping-filtered context, re-rendered
   * with the reserved {@code __}-prefixed keys excluded rather than the literal {@code userInput}
   * sent to the LLM: {@code ContextMapping.none()} (empty {@code inputKeys}) renders every
   * context entry, including runtime bookkeeping such as
   * {@code ReservedContextKeys#LLM_TOKENS_TOTAL}, which {@code LlmCallObserver} updates after
   * every call — fingerprinting that raw render would make the "unchanged context" comparison
   * spuriously fail on the very next invocation regardless of whether anything task-relevant
   * changed. {@code inputFingerprint} fingerprints that same filtered context together with the
   * step's static prompt material, so a signal distinguishes "only the context changed" from "the
   * effective input is unchanged" when a step declares its own {@code stepPrompt}.
   *
   * <p><strong>Known limitation:</strong> the evaluators' {@code isRetry} parameter (which exists
   * so a deliberate {@code RETRY_PREVIOUS} re-invocation is never flagged) is always passed
   * {@code false} here — {@code AgentInvoker} has no signal distinguishing a runtime-driven retry
   * from an ordinary repeat invocation, and adding one is a larger change than this wiring pass.
   * Advisory-only and safe by construction: {@link WasteSignalPolicy#NO_OP} is the shipped
   * default, so this can only ever produce an extra, harmless audit event, never alter execution.
   */
  private void evaluateWasteSignals(WorkflowState state, AgentDefinition agent,
      ContextMapping contextMapping, String stepPrompt, ModelTier requestedTier) {
    String stepId = state.getCurrentStepId();
    if (stepId == null) {
      return;
    }
    Map<String, ContextValue> nonReservedContext = state.getContext().entrySet().stream()
        .filter(entry -> !entry.getKey().startsWith("__"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
            (first, second) -> first, LinkedHashMap::new));
    String fingerprintSource = contextRenderer.render(nonReservedContext, contextMapping);
    String scopedContextFingerprint = ContextFingerprint.of(fingerprintSource);
    String inputFingerprint = ContextFingerprint.of(
        fingerprintSource + ' ' + StringUtils.defaultString(stepPrompt));
    Optional<WasteDetectorInvocationHistory> prior = WasteDetectorHistoryStore.readInvocation(
        state, stepId, objectMapper);
    String priorContextFingerprint = prior.map(WasteDetectorInvocationHistory::scopedContextFingerprint)
        .orElse(null);
    String priorInputFingerprint = prior.map(WasteDetectorInvocationHistory::inputFingerprint)
        .orElse(null);
    ModelTier priorResolvedTier = prior.map(WasteDetectorInvocationHistory::resolvedTier)
        .orElse(null);

    WasteDetector.evaluateDuplicateInvocation(stepId, agent.id(), scopedContextFingerprint,
        inputFingerprint, priorContextFingerprint, priorInputFingerprint, false)
        .ifPresent(signal -> recordWasteSignal(state, signal));
    if (requestedTier != null) {
      WasteDetector.evaluateUnjustifiedTierEscalation(stepId, agent.id(), requestedTier,
          priorResolvedTier, scopedContextFingerprint, priorContextFingerprint, inputFingerprint,
          priorInputFingerprint)
          .ifPresent(signal -> recordWasteSignal(state, signal));
    }

    WasteDetectorHistoryStore.writeInvocation(state, new WasteDetectorInvocationHistory(stepId,
        agent.id(), scopedContextFingerprint, inputFingerprint, requestedTier), objectMapper);
  }

  private void recordWasteSignal(WorkflowState state, TokenGovernanceSignal signal) {
    wasteSignalPolicy.onSignal(signal);
    String agentIdPart = signal.agentId() != null ? " agentId=%s".formatted(signal.agentId()) : "";
    eventRecorder.record(state.getRunId(), signal.stepId(), WorkflowEventType.TOKEN_GOVERNANCE_SIGNAL,
        "kind=%s%s detail=%s".formatted(signal.kind(), agentIdPart, signal.detail()), "runtime");
  }

  /**
   * Resolves the concrete model and its source for this call, applying precedence: a raw model pin on the selected
   * provider preference wins; otherwise an effective tier (step tier overriding agent tier) is resolved via the
   * {@link ModelTierResolver}; otherwise no model is sent and the provider default is used. A declared tier that cannot
   * be resolved throws {@link ModelTierResolutionException} rather than silently downgrading.
   */
  private ModelResolution resolveModel(AgentDefinition agent,
      ProviderPreference preference,
      String stepModelTier) {
    if (StringUtils.isNotBlank(preference.model())) {
      return new ModelResolution(preference.model(), ModelSource.PIN, null);
    }
    String effectiveTierName = StringUtils.defaultIfBlank(stepModelTier, agent.modelTier());
    if (StringUtils.isNotBlank(effectiveTierName)) {
      ModelTier tier = parseTier(effectiveTierName, agent.id());
      String resolved = modelTierResolver.resolve(preference.provider(), tier);
      Validate.notNull(resolved, () -> new ModelTierResolutionException(
          "No model mapping for provider '%s' and tier %s (agent '%s')".formatted(
              preference.provider(), tier, agent.id())));
      return new ModelResolution(resolved, ModelSource.TIER, tier);
    }
    return new ModelResolution(null, ModelSource.PROVIDER_DEFAULT, null);
  }

  private static ModelTier parseTier(String tierName, String agentId) {
    try {
      return ModelTier.fromName(tierName);
    } catch (IllegalArgumentException e) {
      throw new ModelTierResolutionException(
          "Invalid model tier '%s' for agent '%s'; valid tiers: %s".formatted(
              tierName, agentId, ModelTier.joinedNames()));
    }
  }

  private record ModelResolution(String resolvedModel, ModelSource modelSource,
                                 ModelTier requestedModelTier) {

  }

  private ParsedInvocation invokeLlmRecordAndParseWithRetry(
      AgentDefinition agent,
      ProviderPreference preference,
      ModelResolution resolution,
      LlmClient client,
      AssembledSystemPrompt assembled,
      String originalUserInput,
      CommandResponseSchema schema,
      WorkflowState state,
      String actorIdForEvents,
      String activeWorkflowId) {
    String effectiveModel = resolution.resolvedModel();
    String correctionBody = "";

    LOG.log(System.Logger.Level.DEBUG, "Dispatching LLM call provider={0}, model={1}",
        preference.provider(), effectiveModel);
    LlmInvocationIdentity identity = new LlmInvocationIdentity(
        activeWorkflowId, state.getRunId(), state.getCurrentStepId(), agent.id());
    LlmCommandParseException lastParseFailure = null;
    for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
      String effectiveUserInput = toCorrectedPrompt(originalUserInput, attempt, correctionBody);

      LlmExecutionResponse response = executeLlmCall(agent, preference, effectiveModel, client,
          new LlmExecutionRequest(
              preference.provider(),
              effectiveModel,
              assembled.text(),
              effectiveUserInput,
              null,
              assembled.promptLayerBoundaries(),
              identity),
          attempt > 1);

      String responseText = response.text();
      LOG.log(System.Logger.Level.DEBUG, "{0} LLM response received charCount={1}",
          (attempt == 1) ? "Raw" : "Retry", responseText.length());

      recordLlmOutput(state, actorIdForEvents, responseText);
      try {
        return new ParsedInvocation(response, llmCommandParser.parse(responseText, schema), attempt);
      } catch (LlmCommandParseException e) {
        // The provider call above is real and metered regardless of parse outcome — record it now so
        // a superseded/exhausted attempt's usage is never lost, even though its output is discarded.
        llmCallObserver.recordAttempt(actorIdForEvents, preference.provider(), response,
            resolution.resolvedModel(), resolution.modelSource(), resolution.requestedModelTier(),
            state, attempt);
        lastParseFailure = e;
        if (attempt < RETRY_ATTEMPTS) {
          correctionBody = handleFailedLlmResponseParse(e);
        } else {
          LOG.log(System.Logger.Level.ERROR, "LLM command output invalid: {0}", e.getMessage());
        }
      }
    }
    throw lastParseFailure;
  }

  private static String handleFailedLlmResponseParse(LlmCommandParseException e) {
    LOG.log(System.Logger.Level.DEBUG,
        "LLM command output failed validation; retrying: " + e.getMessage(), e);
    return "CORRECTION REQUIRED (your prior reply broke the command contract): %s".formatted(
        e.getMessage());
  }

  private static String toCorrectedPrompt(String prompt, int attempt, String correctionBody) {
    if (attempt == 1) {
      return prompt;
    }
    return prompt + System.lineSeparator() + System.lineSeparator() + correctionBody;
  }

  private LlmExecutionResponse executeLlmCall(
      AgentDefinition agent,
      ProviderPreference preference,
      String effectiveModel,
      LlmClient client,
      LlmExecutionRequest request,
      boolean retryAttempt) {
    try {
      return client.execute(request);
    } catch (RuntimeException e) {
      String template = retryAttempt
          ? "LLM retry call failed agentId={0}, provider={1}, model={2}, message={3}"
          : "LLM call failed agentId={0}, provider={1}, model={2}, message={3}";
      LOG.log(System.Logger.Level.ERROR, template,
          agent.id(), preference.provider(), effectiveModel, e.getMessage());
      throw e;
    }
  }

  /**
   * Records raw LLM output to the event log, applying {@link #llmOutputEventCharCap} when set.
   */
  private void recordLlmOutput(WorkflowState state, String actorId, String responseText) {
    eventRecorder.record(
        state.getRunId(),
        state.getCurrentStepId(),
        WorkflowEventType.LLM_OUTPUT,
        cappedLlmOutputPayload(responseText),
        actorId);
  }

  private String cappedLlmOutputPayload(String responseText) {
    if (llmOutputEventCharCap == 0 || responseText.length() <= llmOutputEventCharCap) {
      return responseText;
    }
    return responseText.substring(0, llmOutputEventCharCap)
        + "... [event payload truncated for audit; original length=%d chars]".formatted(
        responseText.length());
  }

  private record ParsedInvocation(LlmExecutionResponse llmResponse, List<LlmCommand> commands,
                                   int attempt) {

  }

  /**
   * Assembles the system prompt from trusted layers: agent system prompt, then the framework command contract, then the
   * constant system-rules block (untrusted-input handling), then optional static step material. The agent and
   * framework+rules layers form the cacheable prefix (layer 1 / layer 2); the system-rules block is constant, so it
   * stays within the stable layer-2 region.
   */
  private AssembledSystemPrompt assembleSystemPrompt(
      AgentDefinition agent, String stepPrompt, CommandResponseSchema schema) {
    String frameworkBlock = schemaRenderer.render(schema);
    String rulesBlock = systemRulesProvider.systemRules();
    String layerSeparator = System.lineSeparator() + System.lineSeparator();
    String agentBlock = agent.systemPrompt();
    StringBuilder promptBuilder = new StringBuilder()
        .append(agentBlock)
        .append(layerSeparator)
        .append(frameworkBlock)
        .append(layerSeparator)
        .append(rulesBlock);
    boolean hasStepLayer = appendStepPrompt(stepPrompt, promptBuilder, layerSeparator);
    String prompt = promptBuilder.toString();
    PromptLayerBoundaries boundaries = null;
    if (promptCacheEnabled) {
      boundaries = computePromptLayerBoundaries(
          frameworkBlock, rulesBlock, layerSeparator, agentBlock, hasStepLayer, prompt);
    }
    return new AssembledSystemPrompt(prompt, boundaries);
  }

  /**
   * Appends a tool-capabilities section (uncached suffix, so prompt-cache boundaries are unchanged) when a
   * {@link ToolCatalog} is configured, the agent has opted into {@code TOOL_INVOCATION}, and the catalog is non-empty
   * for the run's scope. Otherwise returns {@code assembled} unchanged.
   */
  private AssembledSystemPrompt appendToolCatalog(AssembledSystemPrompt assembled,
      CommandResponseSchema schema, WorkflowState state) {
    if (toolCatalog == null
        || !schema.supportedCommandTypes().contains(TOOL_INVOCATION_COMMAND)) {
      return assembled;
    }
    List<ToolDescriptor> tools = toolCatalog.available(
        new ToolScope(state.getWorkflowId(), state.getRunId()));
    if (tools == null || tools.isEmpty()) {
      return assembled;
    }
    String separator = System.lineSeparator() + System.lineSeparator();
    return new AssembledSystemPrompt(
        assembled.text() + separator + renderToolCatalog(tools),
        assembled.promptLayerBoundaries());
  }

  private static String renderToolCatalog(List<ToolDescriptor> tools) {
    StringBuilder builder = new StringBuilder();
    builder.append("Available tools. To request one, emit a TOOL_INVOCATION command where ")
        .append("\"arguments\" is a JSON object matching the tool's input schema (an object, not a ")
        .append("string): ")
        .append("{ \"type\": \"TOOL_INVOCATION\", \"capability\": \"<capability>\", ")
        .append("\"arguments\": { ... } }.")
        .append(System.lineSeparator())
        .append("Only request a capability listed below. The runtime decides whether to execute it; ")
        .append("you never reach the external system directly.")
        .append(System.lineSeparator());
    for (ToolDescriptor tool : tools) {
      builder.append("- ").append(tool.capability());
      if (StringUtils.isNotBlank(tool.description())) {
        builder.append(": ").append(tool.description());
      }
      if (StringUtils.isNotBlank(tool.inputSchema())) {
        builder.append(System.lineSeparator())
            .append("  input schema: ").append(tool.inputSchema());
      }
      builder.append(System.lineSeparator());
    }
    return builder.toString().stripTrailing();
  }

  private static boolean appendStepPrompt(String stepPrompt, StringBuilder promptBuilder,
      String layerSeparator) {
    boolean hasStepLayer = StringUtils.isNotBlank(stepPrompt);
    if (hasStepLayer) {
      promptBuilder.append(layerSeparator).append(stepPrompt);
    }
    return hasStepLayer;
  }

  private static PromptLayerBoundaries computePromptLayerBoundaries(
      String frameworkBlock,
      String rulesBlock,
      String layerSeparator,
      String agentBlock,
      boolean hasStepLayer,
      String assembledPrompt) {
    int layer1End = utf8ByteLength(agentBlock);
    // Layer 2 spans the trusted cacheable prefix: agent + framework + the constant system-rules block.
    int layer2End = utf8ByteLength(agentBlock + layerSeparator + frameworkBlock + layerSeparator + rulesBlock);
    Integer layer3End = hasStepLayer ? utf8ByteLength(assembledPrompt) : null;
    return new PromptLayerBoundaries(layer1End, layer2End, layer3End);
  }

  private static int utf8ByteLength(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  private record AssembledSystemPrompt(String text, PromptLayerBoundaries promptLayerBoundaries) {

  }
}
