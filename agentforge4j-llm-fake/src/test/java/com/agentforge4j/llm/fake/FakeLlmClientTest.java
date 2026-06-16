// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.api.TokenUsageReport;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeLlmClientTest {

  private static final String WF = "recruitment";
  private static final String RUN = "run-1";
  private static final String STEP = "screen-cv";
  private static final String AGENT = "screener";

  @Test
  void found_returnsScriptedTextModelEchoAndExplicitTokenUsage() {
    FakeScript script = scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0),
        new FakeResponse("scripted reply", new FakeTokenUsage(120, 64, 8, null)));
    FakeLlmClient client = clientFor(RUN, script);

    LlmExecutionResponse response = client.execute(request(WF, RUN, STEP, AGENT, "gpt-x"));

    assertThat(response.text()).isEqualTo("scripted reply");
    assertThat(response.modelUsed()).isEqualTo("gpt-x");
    assertThat(response.tokenUsage()).isEqualTo(new TokenUsageReport(120, 64, 8, null));
  }

  @Test
  void omittedTokenUsage_usesDeterministicLengthFallback() {
    FakeScript script = scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("hello", null));
    FakeLlmClient client = clientFor(RUN, script);

    LlmExecutionResponse response = client.execute(request(WF, RUN, STEP, AGENT, null));

    // systemPrompt(13) + userInput(10) = 23 -> ceil(23/4)=6 ; responseText "hello"(5) -> ceil(5/4)=2
    assertThat(response.tokenUsage()).isEqualTo(new TokenUsageReport(6, 2, null, null));
  }

  @Test
  void nullIdentity_failsClosed() {
    FakeLlmClient client = clientFor(RUN, scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("x", null)));
    LlmExecutionRequest noIdentity = new LlmExecutionRequest("fake", null, "system", "user", null, null, null);

    assertThatThrownBy(() -> client.execute(noIdentity))
        .isInstanceOf(FakeResponseNotFoundException.class)
        .hasMessageContaining("identity");
  }

  @Test
  void noScriptForRun_failsClosed_namesRun() {
    FakeLlmClient client = new FakeLlmClient(new RegistryFakeResponseSource(new FakeRunLifecycle()));

    assertThatThrownBy(() -> client.execute(request(WF, "run-unregistered", STEP, AGENT, null)))
        .isInstanceOf(FakeResponseNotFoundException.class)
        .hasMessageContaining("No fake script registered for run 'run-unregistered'");
  }

  @Test
  void missingKey_failsClosed_namesFullKey() {
    FakeLlmClient client = clientFor(RUN, scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("only-zero", null)));
    // ordinal 0 consumed; ordinal 1 is absent
    client.execute(request(WF, RUN, STEP, AGENT, null));

    assertThatThrownBy(() -> client.execute(request(WF, RUN, STEP, AGENT, null)))
        .isInstanceOf(FakeResponseNotFoundException.class)
        .hasMessageContaining("ordinal=1")
        .hasMessageContaining(WF)
        .hasMessageContaining(STEP)
        .hasMessageContaining(AGENT);
  }

  @Test
  void ordinalAdvances_acrossCallsForSameKey_loopReentry() {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("iter-0", null),
        new FakeScriptKey(WF, STEP, AGENT, 1), new FakeResponse("iter-1", null),
        new FakeScriptKey(WF, STEP, AGENT, 2), new FakeResponse("iter-2", null)));
    FakeLlmClient client = clientFor(RUN, script);

    assertThat(client.execute(request(WF, RUN, STEP, AGENT, null)).text()).isEqualTo("iter-0");
    assertThat(client.execute(request(WF, RUN, STEP, AGENT, null)).text()).isEqualTo("iter-1");
    assertThat(client.execute(request(WF, RUN, STEP, AGENT, null)).text()).isEqualTo("iter-2");
  }

  @Test
  void sparAgentAlternation_inOneStep_keepsSeparateOrdinalSequences() {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WF, STEP, "primary", 0), new FakeResponse("primary-0", null),
        new FakeScriptKey(WF, STEP, "primary", 1), new FakeResponse("primary-1", null),
        new FakeScriptKey(WF, STEP, "challenger", 0), new FakeResponse("challenger-0", null)));
    FakeLlmClient client = clientFor(RUN, script);

    assertThat(client.execute(request(WF, RUN, STEP, "primary", null)).text()).isEqualTo("primary-0");
    assertThat(client.execute(request(WF, RUN, STEP, "challenger", null)).text())
        .isEqualTo("challenger-0");
    assertThat(client.execute(request(WF, RUN, STEP, "primary", null)).text()).isEqualTo("primary-1");
  }

  @Test
  void singleClient_servesConcurrentRunsIsolated() {
    FakeRunLifecycle lifecycle = new FakeRunLifecycle();
    lifecycle.register("run-a", scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("a-0", null)));
    lifecycle.register("run-b", scriptOf(
        new FakeScriptKey(WF, STEP, AGENT, 0), new FakeResponse("b-0", null)));
    FakeLlmClient client = new FakeLlmClient(new RegistryFakeResponseSource(lifecycle));

    // Same workflow/step/agent, different runs — sequences stay isolated.
    assertThat(client.execute(request(WF, "run-a", STEP, AGENT, null)).text()).isEqualTo("a-0");
    assertThat(client.execute(request(WF, "run-b", STEP, AGENT, null)).text()).isEqualTo("b-0");
  }

  private static FakeLlmClient clientFor(String runId, FakeScript script) {
    FakeRunLifecycle lifecycle = new FakeRunLifecycle();
    lifecycle.register(runId, script);
    return new FakeLlmClient(new RegistryFakeResponseSource(lifecycle));
  }

  private static FakeScript scriptOf(FakeScriptKey key, FakeResponse response) {
    return new FakeScript(1, Map.of(key, response));
  }

  private static LlmExecutionRequest request(String workflowId, String runId, String stepId,
      String agentId, String model) {
    return new LlmExecutionRequest("fake", model, "system-prompt", "user-input", null, null,
        new LlmInvocationIdentity(workflowId, runId, stepId, agentId));
  }
}
