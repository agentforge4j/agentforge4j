// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorContextSelectionTest {

  private final WorkflowValidator validator = new WorkflowValidator();

  private static ContextSelector sel(ContextSourceKind kind, String ref) {
    return new ContextSelector(kind, ref, ContextVariant.FULL);
  }

  private static LedgerDefinition ledger(String id) {
    return new LedgerDefinition(id, "ledger/requirement-ledger.schema.json",
        LedgerMergeStrategy.APPEND, null);
  }

  private static StepDefinition step(String id) {
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop")).build();
  }

  private static StepDefinition stepWithSelection(String id, ContextSelection selection) {
    return StepDefinition.builder().withStepId(id).withName(id)
        .withBehaviour(new FailBehaviour("stop")).withContextSelection(selection).build();
  }

  private static WorkflowDefinition workflow(List<Executable> steps,
      List<LedgerDefinition> ledgers) {
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), steps, List.of(),
        ledgers);
  }

  private void validate(WorkflowDefinition wf) {
    validator.validateContextSelectionRefs(Map.of("wf", wf));
  }

  @Test
  void acceptsResolvableLedgerAndStepSelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "requirements.entries"),
            sel(ContextSourceKind.STEP_OUTPUT, "s1")),
        List.of());
    WorkflowDefinition wf = workflow(
        List.of(step("s1"), stepWithSelection("s2", selection)),
        List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownLedgerSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of());
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void rejectsUnknownStepOutputSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "ghost")), List.of());
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown step 'ghost'");
  }

  @Test
  void rejectsUnknownArtifactSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.ARTIFACT, "missing")), List.of());
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown artifact 'missing'");
  }

  @Test
  void skipsContextPackAndStateKeySelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "any-pack"),
            sel(ContextSourceKind.STATE_KEY, "some-key")),
        List.of());
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void validatesExpandableScopeSelectors() {
    ContextSelection selection = new ContextSelection(List.of(),
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")));
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void validatesCompactStepSourceSelector() {
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "nope"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }
}
