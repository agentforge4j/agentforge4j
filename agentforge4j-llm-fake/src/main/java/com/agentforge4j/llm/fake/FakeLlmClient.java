package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.util.Validate;

/**
 * Stateless {@link LlmClient} that replays pre-scripted responses instead of calling a model. It holds only its
 * {@link FakeResponseSource}; all per-run state (scripts and ordinal counters) lives in the source's store, so a single
 * instance safely serves concurrent runs.
 *
 * <p>Keying: the response is selected by the request's {@link LlmInvocationIdentity}
 * ({@code workflowId}, {@code runId}, {@code stepId}, {@code agentId}) plus a per-sequence ordinal the source advances.
 * Every resolution failure is fail-closed via {@link FakeResponseNotFoundException}; the fake never fabricates a
 * default.
 */
public final class FakeLlmClient implements LlmClient {

  /**
   * Token-estimate divisor for the deterministic length-based usage fallback.
   */
  private static final int FALLBACK_CHARS_PER_TOKEN = 4;

  private static final String PROVIDER_NAME = "fake";

  private final FakeResponseSource responseSource;

  /**
   * Creates a client backed by the given response source.
   *
   * @param responseSource the scripted response source; must not be {@code null}
   */
  public FakeLlmClient(FakeResponseSource responseSource) {
    this.responseSource = Validate.notNull(responseSource, "responseSource must not be null");
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public LlmExecutionResponse execute(LlmExecutionRequest request) {
    Validate.notNull(request, "request must not be null");
    LlmInvocationIdentity identity = request.identity();
    Validate.notNull(identity, () -> new FakeResponseNotFoundException(
        "Fake provider requires an invocation identity on the request, but none was present"));

    FakeInvocation invocation = new FakeInvocation(identity.workflowId(), identity.runId(), identity.stepId(),
        identity.agentId());
    FakeResolution resolution = responseSource.nextResponse(invocation);

    if (resolution instanceof FakeResolution.Found found) {
      FakeResponse response = found.response();
      return new LlmExecutionResponse(response.responseText(), request.model(), toTokenUsageReport(response, request));
    } else if (resolution instanceof FakeResolution.RunNotScripted) {
      throw new FakeResponseNotFoundException(
          "No fake script registered for run '%s' (provider 'fake')".formatted(invocation.runId()));
    } else if (resolution instanceof FakeResolution.KeyAbsent keyAbsent) {
      throw new FakeResponseNotFoundException(missingKeyMessage(invocation, keyAbsent.key()));
    }
    throw new IllegalStateException("Unhandled FakeResolution: " + resolution);
  }

  private static String missingKeyMessage(FakeInvocation invocation, FakeScriptKey key) {
    return ("No fake response for key (workflowId=%s, stepId=%s, agentId=%s, ordinal=%d) on run "
        + "'%s'. Add this entry to the script registered for run '%s'.").formatted(key.workflowId(), key.stepId(),
        key.agentId(), key.ordinal(), invocation.runId(), invocation.runId());
  }

  private static TokenUsageReport toTokenUsageReport(FakeResponse response, LlmExecutionRequest request) {
    FakeTokenUsage usage = response.tokenUsage();
    if (usage != null) {
      return new TokenUsageReport(usage.inputTokens(), usage.outputTokens(), usage.cachedInputTokens(),
          usage.cacheWriteTokens());
    }
    int inputTokens = ceilDiv(request.systemPrompt().length() + request.userInput().length());
    int outputTokens = ceilDiv(response.responseText().length());
    return new TokenUsageReport(inputTokens, outputTokens, null, null);
  }

  private static int ceilDiv(int value) {
    return (value + FALLBACK_CHARS_PER_TOKEN - 1) / FALLBACK_CHARS_PER_TOKEN;
  }
}
