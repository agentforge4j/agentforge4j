// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.governance.WasteSignalKind;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.testkit.capture.CaptureBundle;
import com.agentforge4j.testkit.capture.CapturingFileSink;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke-runs {@link TestkitModuleConsumer} at runtime. The real JPMS contract proof is that the
 * consumer module compiles while {@code requires}-ing only {@code agentforge4j.testkit}; these tests
 * additionally confirm the re-exported types behave when used.
 */
class TestkitModuleConsumerTest {

  private static final String SCRIPT_JSON = """
      {
        "schemaVersion": 1,
        "responses": [
          {
            "workflowId": "wf",
            "stepId": "step",
            "agentId": "agent",
            "ordinal": 0,
            "responseText": "[{\\"type\\":\\"COMPLETE\\"}]"
          }
        ]
      }
      """;

  private final TestkitModuleConsumer consumer = new TestkitModuleConsumer();

  @Test
  void loadsFakeScriptThroughTestkitApi() {
    FakeScript script = consumer.loadScript(SCRIPT_JSON);

    assertThat(script).isNotNull();
  }

  @Test
  void buildsCapturingFileSinkAsRuntimeFileSink() {
    FileSink sink = consumer.newFileSink();

    assertThat(sink).isInstanceOf(CapturingFileSink.class);
  }

  @Test
  void callsProviderCallTierWithModelTier() {
    WorkflowEvent llmCall = new WorkflowEvent("e1", "run-1", null,
        WorkflowEventType.LLM_CALL_COMPLETED, "{\"requestedModelTier\":\"STANDARD\"}", "runtime",
        Instant.EPOCH);
    WorkflowRunResult result = new WorkflowRunResult("run-1",
        new WorkflowState("run-1", "wf-1", null, Instant.EPOCH),
        new CaptureBundle(List.of(llmCall), List.of()));

    assertThatCode(() -> consumer.assertProviderTier(result, ModelTier.STANDARD))
        .doesNotThrowAnyException();
  }

  @Test
  void resolvesContextPackRegistryFromAnExternalModule() {
    ContextPackRegistry registry = consumer.emptyContextPackRegistry();

    assertThat(registry).isSameAs(ContextPackRegistry.EMPTY);
    assertThat(registry.names()).isEmpty();
  }

  @Test
  void constructsCompactSiblingUnavailableExceptionFromAnExternalModule() {
    String message = consumer.describeCompactSiblingUnavailable("ledger:requirements", "abc",
        "def");

    assertThat(message).contains("ledger:requirements").contains("abc").contains("def");
  }

  @Test
  void constructsContextPackFromAnExternalModule() {
    ContextPack pack = consumer.contextPackOf("my-pack", "full", "content");

    assertThat(pack.name()).isEqualTo("my-pack");
    assertThat(pack.variants().get("full").content()).isEqualTo("content");
  }

  @Test
  void constructsAndReactsToTokenGovernanceSignalFromAnExternalModule() {
    WasteSignalKind kind = consumer.describeWasteSignal("step-1", "duplicate detected");

    assertThat(kind).isEqualTo(WasteSignalKind.DUPLICATE_INVOCATION);
  }
}
