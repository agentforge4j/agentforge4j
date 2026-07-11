// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.consumer;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.contextpack.ContextPackVariant;
import com.agentforge4j.core.spi.governance.TokenGovernanceSignal;
import com.agentforge4j.core.spi.governance.WasteSignalKind;
import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.runtime.ContextPackRegistry;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.exception.CompactSiblingUnavailableException;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.CapturingFileSink;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import java.util.Map;

/**
 * Exercises representative public testkit signatures whose parameter/return types come from modules
 * the testkit only re-exports transitively: {@link FakeScript} ({@code agentforge4j.llm.fake}),
 * {@link ModelTier} ({@code agentforge4j.llm.api}), and {@link FileSink} ({@code agentforge4j.runtime},
 * the supertype of the testkit's {@link CapturingFileSink}). {@link ContextPackRegistry} (top-level
 * {@code com.agentforge4j.runtime}), {@link CompactSiblingUnavailableException}
 * ({@code com.agentforge4j.runtime.exception}), {@link ContextPack}/{@link ContextPackVariant} and
 * {@link TokenGovernanceSignal}/{@link WasteSignalKind}/{@link WasteSignalPolicy}
 * ({@code com.agentforge4j.core.spi.contextpack}/{@code .governance}, reached via
 * {@code agentforge4j.testkit}'s {@code requires transitive agentforge4j.core}) are not part of any
 * testkit signature but are exercised directly here as the JPMS export proof for those types: module
 * readability from a transitive {@code requires} extends to every type their exported packages
 * export, not only the ones the testkit's own API happens to use. This class compiling against a
 * module that {@code requires} only {@code agentforge4j.testkit} is the JPMS contract proof.
 */
public final class TestkitModuleConsumer {

  /**
   * Loads a {@link FakeScript} through the testkit loader — naming a {@code agentforge4j.llm.fake}
   * type returned by the testkit API.
   *
   * @param scriptJson the fake-script JSON; must be a valid script
   *
   * @return the parsed script
   */
  public FakeScript loadScript(String scriptJson) {
    return new ScenarioScriptLoader().fromJson(scriptJson);
  }

  /**
   * Creates a testkit {@link CapturingFileSink} and returns it as the {@code agentforge4j.runtime}
   * {@link FileSink} supertype it implements.
   *
   * @return a new capturing file sink
   */
  public FileSink newFileSink() {
    return new CapturingFileSink();
  }

  /**
   * Calls {@link WorkflowRunAssert#providerCallTier(ModelTier)} — naming the
   * {@code agentforge4j.llm.api} {@link ModelTier} type in a testkit signature.
   *
   * @param result the run result; must not be {@code null}
   * @param tier   the expected provider-call tier; must not be {@code null}
   *
   * @return the assertion, for chaining
   */
  public WorkflowRunAssert assertProviderTier(WorkflowRunResult result, ModelTier tier) {
    return WorkflowRunAssert.assertThat(result).providerCallTier(tier);
  }

  /**
   * Returns the empty {@link ContextPackRegistry} — naming a top-level {@code com.agentforge4j.runtime}
   * type used as a {@code WorkflowRuntimeBuilder} parameter.
   *
   * @return the empty registry
   */
  public ContextPackRegistry emptyContextPackRegistry() {
    return ContextPackRegistry.EMPTY;
  }

  /**
   * Constructs a {@link CompactSiblingUnavailableException} and returns its message — naming a
   * {@code com.agentforge4j.runtime.exception} type reachable only because that package is
   * exported by {@code agentforge4j.runtime}. This is the exception a {@code COMPACT_ONLY}
   * context-expansion grant propagates to an embedder when no fresh compact sibling exists.
   *
   * @param sourceId             the source id the exception names; must not be blank
   * @param expectedFingerprint  the compact sibling's expected fingerprint
   * @param actualFingerprint    the source's actual current fingerprint
   *
   * @return the exception's message
   */
  public String describeCompactSiblingUnavailable(String sourceId, String expectedFingerprint,
      String actualFingerprint) {
    return new CompactSiblingUnavailableException(sourceId, expectedFingerprint, actualFingerprint)
        .getMessage();
  }

  /**
   * Constructs a single-variant {@link ContextPack} — naming a
   * {@code com.agentforge4j.core.spi.contextpack} type reachable only because that package is
   * exported by {@code agentforge4j.core} and re-exported transitively through
   * {@code agentforge4j.testkit}.
   *
   * @param name    the pack name; must not be blank
   * @param variant the single variant's name; must not be blank
   * @param content the variant's content; must not be {@code null}
   *
   * @return the constructed pack
   */
  public ContextPack contextPackOf(String name, String variant, String content) {
    ContextPackVariant packVariant = new ContextPackVariant(variant, content, "sha256:test");
    return new ContextPack(name, "1", null, null, Map.of(variant, packVariant));
  }

  /**
   * Constructs a {@link TokenGovernanceSignal} and hands it to {@link WasteSignalPolicy#NO_OP} —
   * naming both {@code com.agentforge4j.core.spi.governance} types reachable only because that
   * package is exported by {@code agentforge4j.core} and re-exported transitively through
   * {@code agentforge4j.testkit}.
   *
   * @param stepId the step the signal concerns; must not be blank
   * @param detail human-readable detail; must not be blank
   *
   * @return the constructed signal's {@link WasteSignalKind}
   */
  public WasteSignalKind describeWasteSignal(String stepId, String detail) {
    TokenGovernanceSignal signal =
        new TokenGovernanceSignal(WasteSignalKind.DUPLICATE_INVOCATION, stepId, null, detail);
    WasteSignalPolicy.NO_OP.onSignal(signal);
    return signal.kind();
  }
}
