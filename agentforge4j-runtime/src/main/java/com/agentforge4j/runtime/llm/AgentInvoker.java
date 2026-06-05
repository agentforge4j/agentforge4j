package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandResponseSchemaRenderer;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolutionException;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Drives a single agent invocation: resolves the agent definition, picks the first enabled provider
 * preference, renders the input context as JSON, executes against the resolved {@link LlmClient},
 * and parses the structured command output.
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
  private final CommandResponseSchemaRenderer schemaRenderer = new CommandResponseSchemaRenderer();

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

    private Builder() {

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
   * Invokes an agent, optionally overriding the agent's capability tier for this step.
   *
   * @param agentId        the agent to invoke; must not be blank
   * @param contextMapping context mapping for input rendering; must not be {@code null}
   * @param state          mutable run state; must not be {@code null}
   * @param stepPrompt     optional static step prompt material; may be blank
   * @param stepModelTier  optional step-level tier name overriding the agent tier; {@code null} or
   *                       blank inherits the agent tier
   *
   * @return the parsed invocation result; never {@code null}
   */
  public AgentInvocationResult invoke(String agentId,
      ContextMapping contextMapping,
      WorkflowState state,
      String stepPrompt,
      String stepModelTier) {
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(state, "state must not be null");

    AgentDefinition agent = agentRepository.get(agentId);
    Validate.isTrue(agent.enabled(),
        "Agent '%s' is disabled and cannot be invoked".formatted(agent.id()));

    String userInput = contextRenderer.render(state.getContext(), contextMapping);
    return invokeWithAudit(agent, userInput, stepPrompt, stepModelTier, state, agentId);
  }

  private AgentInvocationResult invokeWithAudit(AgentDefinition agent,
      String userInput,
      String stepPrompt,
      String stepModelTier,
      WorkflowState state,
      String actorIdForEvents) {
    ProviderPreference preference = llmProviderSelectionStrategy.selectInitialProvider(
        agent, llmClientResolver.listAvailableClients());
    ModelResolution resolution = resolveModel(agent, preference, stepModelTier);
    LOG.log(System.Logger.Level.DEBUG,
        "Agent invoker entry agentId={0}, provider={1}, model={2}, modelSource={3}",
        agent.id(), preference.provider(), resolution.resolvedModel(), resolution.modelSource());
    LlmClient client = llmClientResolver.resolve(preference.provider());
    CommandResponseSchema schema = CommandSchemaFactory.build(agent.supportedCommands(),
        objectMapper);
    AssembledSystemPrompt assembled = assembleSystemPrompt(agent, stepPrompt, schema);

    ParsedInvocation parsed = invokeLlmRecordAndParseWithRetry(
        agent, preference, resolution.resolvedModel(), client, assembled, userInput, schema, state,
        actorIdForEvents);
    llmCallObserver.observe(actorIdForEvents, preference.provider(), parsed.llmResponse(),
        resolution.resolvedModel(), resolution.modelSource(), resolution.requestedModelTier(),
        state);
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
   * Resolves the concrete model and its source for this call, applying precedence: a raw model pin
   * on the selected provider preference wins; otherwise an effective tier (step tier overriding
   * agent tier) is resolved via the {@link ModelTierResolver}; otherwise no model is sent and the
   * provider default is used. A declared tier that cannot be resolved throws
   * {@link ModelTierResolutionException} rather than silently downgrading.
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
      return ModelTier.valueOf(tierName.trim());
    } catch (IllegalArgumentException e) {
      throw new ModelTierResolutionException(
          "Invalid model tier '%s' for agent '%s'; valid tiers: LITE, STANDARD, POWERFUL".formatted(
              tierName, agentId));
    }
  }

  private record ModelResolution(String resolvedModel, ModelSource modelSource,
                                 ModelTier requestedModelTier) {

  }

  private ParsedInvocation invokeLlmRecordAndParseWithRetry(
      AgentDefinition agent,
      ProviderPreference preference,
      String effectiveModel,
      LlmClient client,
      AssembledSystemPrompt assembled,
      String originalUserInput,
      CommandResponseSchema schema,
      WorkflowState state,
      String actorIdForEvents) {
    String correctionBody = "";

    LOG.log(System.Logger.Level.DEBUG, "Dispatching LLM call provider={0}, model={1}",
        preference.provider(), effectiveModel);
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
              assembled.promptLayerBoundaries()),
          attempt > 1);

      String responseText = response.text();
      LOG.log(System.Logger.Level.DEBUG, "{0} LLM response received charCount={1}",
          (attempt == 1) ? "Raw" : "Retry", responseText.length());

      recordLlmOutput(state, actorIdForEvents, responseText);
      try {
        return new ParsedInvocation(response, llmCommandParser.parse(responseText, schema));
      } catch (LlmCommandParseException e) {
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

  private record ParsedInvocation(LlmExecutionResponse llmResponse, List<LlmCommand> commands) {

  }

  /**
   * Assembles the system prompt from three layers (most-stable first): framework command contract,
   * agent system prompt (boundaries already merged at load), then optional static step material.
   */
  private AssembledSystemPrompt assembleSystemPrompt(
      AgentDefinition agent, String stepPrompt, CommandResponseSchema schema) {
    String frameworkBlock = schemaRenderer.render(schema);
    String layerSeparator = System.lineSeparator() + System.lineSeparator();
    String agentBlock = agent.systemPrompt();
    StringBuilder promptBuilder = new StringBuilder()
        .append(agentBlock)
        .append(layerSeparator)
        .append(frameworkBlock);
    boolean hasStepLayer = appendStepPrompt(stepPrompt, promptBuilder, layerSeparator);
    String prompt = promptBuilder.toString();
    PromptLayerBoundaries boundaries = null;
    if (promptCacheEnabled) {
      boundaries = computePromptLayerBoundaries(
          frameworkBlock, layerSeparator, agentBlock, hasStepLayer, prompt);
    }
    return new AssembledSystemPrompt(prompt, boundaries);
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
      String layerSeparator,
      String agentBlock,
      boolean hasStepLayer,
      String assembledPrompt) {
    int layer1End = utf8ByteLength(agentBlock);
    int layer2End = utf8ByteLength(frameworkBlock + layerSeparator + agentBlock);
    Integer layer3End = hasStepLayer ? utf8ByteLength(assembledPrompt) : null;
    return new PromptLayerBoundaries(layer1End, layer2End, layer3End);
  }

  private static int utf8ByteLength(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  private record AssembledSystemPrompt(String text, PromptLayerBoundaries promptLayerBoundaries) {

  }
}
