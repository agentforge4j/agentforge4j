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
import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ContextRenderer contextRenderer;
  private final LlmCommandParser llmCommandParser;
  private final ObjectMapper objectMapper;
  private final EventRecorder eventRecorder;
  private final int llmOutputEventCharCap;
  private final CommandResponseSchemaRenderer schemaRenderer = new CommandResponseSchemaRenderer();

  public AgentInvoker(AgentRepository agentRepository,
      LlmClientResolver llmClientResolver,
      ContextRenderer contextRenderer,
      LlmCommandParser llmCommandParser,
      ObjectMapper objectMapper,
      EventRecorder eventRecorder) {
    this(agentRepository, llmClientResolver, contextRenderer, llmCommandParser, objectMapper,
        eventRecorder, DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP);
  }

  public AgentInvoker(AgentRepository agentRepository,
      LlmClientResolver llmClientResolver,
      ContextRenderer contextRenderer,
      LlmCommandParser llmCommandParser,
      ObjectMapper objectMapper,
      EventRecorder eventRecorder,
      int llmOutputEventCharCap) {
    this.agentRepository = Validate.notNull(agentRepository, "agentRepository must not be null");
    this.llmClientResolver = Validate.notNull(llmClientResolver,
        "llmClientResolver must not be null");
    this.contextRenderer = Validate.notNull(contextRenderer, "contextRenderer must not be null");
    this.llmCommandParser = Validate.notNull(llmCommandParser, "llmCommandParser must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.llmOutputEventCharCap = Validate.isNotNegative(llmOutputEventCharCap,
        "llmOutputEventCharCap must be >= 0").intValue();
  }

  public AgentInvocationResult invoke(String agentId,
      ContextMapping contextMapping,
      WorkflowState state,
      String stepPrompt) {
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(state, "state must not be null");

    AgentDefinition agent = agentRepository.get(agentId);
    Validate.isTrue(agent.enabled(),
        "Agent '%s' is disabled and cannot be invoked".formatted(agent.id()));

    String userInput = contextRenderer.render(state.getContext(), contextMapping);
    return invokeWithAudit(agent, userInput, stepPrompt, state, agentId);
  }

  private AgentInvocationResult invokeWithAudit(AgentDefinition agent,
      String userInput,
      String stepPrompt,
      WorkflowState stateOrNull,
      String actorIdForEvents) {
    ProviderPreference preference = firstProviderPreference(agent);
    LOG.log(System.Logger.Level.DEBUG, "Agent invoker entry agentId={0}, provider={1}, model={2}",
        agent.id(), preference.provider(), preference.model());
    LlmClient client = llmClientResolver.resolve(preference.provider());
    CommandResponseSchema schema = CommandSchemaFactory.build(agent.supportedCommands(),
        objectMapper);
    String systemPrompt = buildSystemPrompt(agent, stepPrompt, schema);

    ParsedInvocation parsed = invokeLlmRecordAndParseWithRetry(
        agent, preference, client, systemPrompt, userInput, schema, stateOrNull, actorIdForEvents);
    return new AgentInvocationResult(parsed.rawResponse(), parsed.commands());
  }

  private ParsedInvocation invokeLlmRecordAndParseWithRetry(
      AgentDefinition agent,
      ProviderPreference preference,
      LlmClient client,
      String systemPrompt,
      String originalUserInput,
      CommandResponseSchema schema,
      WorkflowState stateOrNull,
      String actorIdForEvents) {
    String correctionBody = "";

    LOG.log(System.Logger.Level.DEBUG, "Dispatching LLM call provider={0}, model={1}",
        preference.provider(), preference.model());
    LlmCommandParseException lastParseFailure = null;
    for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
      String effectiveUserInput = toCorrectedPrompt(originalUserInput, attempt, correctionBody);

      String response = executeLlmCall(agent, preference, client,
          new LlmExecutionRequest(
              preference.provider(),
              preference.model(),
              systemPrompt,
              effectiveUserInput),
          attempt > 1);

      LOG.log(System.Logger.Level.DEBUG, "{0} LLM response received charCount={1}",
          (attempt == 1) ? "Raw" : "Retry", response.length());

      recordLlmOutput(stateOrNull, actorIdForEvents, response);
      try {
        return new ParsedInvocation(response, llmCommandParser.parse(response, schema));
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

  private String executeLlmCall(
      AgentDefinition agent,
      ProviderPreference preference,
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
          agent.id(), preference.provider(), preference.model(), e.getMessage());
      throw e;
    }
  }

  /**
   * Records raw LLM output to the event log, applying {@link #llmOutputEventCharCap} when set.
   */
  private void recordLlmOutput(WorkflowState state, String actorId, String rawResponse) {
    eventRecorder.record(
        state.getRunId(),
        state.getCurrentStepId(),
        WorkflowEventType.LLM_OUTPUT,
        cappedLlmOutputPayload(rawResponse),
        actorId);
  }

  private String cappedLlmOutputPayload(String rawResponse) {
    if (llmOutputEventCharCap == 0 || rawResponse.length() <= llmOutputEventCharCap) {
      return rawResponse;
    }
    return rawResponse.substring(0, llmOutputEventCharCap)
        + "... [event payload truncated for audit; original length=%d chars]".formatted(
        rawResponse.length());
  }

  private record ParsedInvocation(String rawResponse, List<LlmCommand> commands) {

  }

  private String buildSystemPrompt(
      AgentDefinition agent, String stepPrompt, CommandResponseSchema schema) {
    String frameworkBlock = schemaRenderer.render(schema);
    StringBuilder prompt = new StringBuilder();
    prompt.append(agent.systemPrompt());
    if (StringUtils.isNotBlank(stepPrompt)) {
      prompt.append(System.lineSeparator())
          .append(System.lineSeparator())
          .append(stepPrompt);
    }
    prompt.append(System.lineSeparator())
        .append(System.lineSeparator())
        .append(frameworkBlock);
    return prompt.toString();
  }

  private ProviderPreference firstProviderPreference(AgentDefinition agent) {
    return agent.providerPreferences().stream()
        .filter(p -> llmClientResolver.isProviderAvailable(p.provider()))
        .findFirst()
        .orElseThrow(() -> new LlmInvocationException(
            "Agent '%s' has no available provider preferences".formatted(agent.id())));
  }
}
