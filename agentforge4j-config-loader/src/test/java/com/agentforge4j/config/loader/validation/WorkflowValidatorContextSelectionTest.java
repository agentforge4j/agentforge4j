// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.contextpack.ContextPackVariant;
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
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
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

  private static ContextSelector sel(ContextSourceKind kind, String ref, ContextVariant variant) {
    return new ContextSelector(kind, ref, variant);
  }

  private static ContextPack pack(String name, String... variantNames) {
    Map<String, ContextPackVariant> variants = new java.util.LinkedHashMap<>();
    for (String variantName : variantNames) {
      variants.put(variantName, new ContextPackVariant(variantName, "content", "fp-" + variantName));
    }
    return new ContextPack(name, "1.0.0", null, List.of(), variants);
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
    return workflow("wf", steps, ledgers, Map.of());
  }

  private static WorkflowDefinition workflow(String id, List<Executable> steps,
      List<LedgerDefinition> ledgers, Map<String, BlueprintDefinition> blueprints) {
    return new WorkflowDefinition(id, "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps, List.of(),
        ledgers);
  }

  private static BlueprintDefinition blueprint(String id, List<Executable> steps) {
    return new BlueprintDefinition(id, id, new BlueprintBehaviour(null, StepTransition.AUTO),
        steps);
  }

  private void validate(WorkflowDefinition wf) {
    validate(wf, Map.of());
  }

  private void validate(WorkflowDefinition wf, Map<String, ContextPack> loadedPacksByName) {
    validator.validateContextSelectionRefs(Map.of("wf", wf), loadedPacksByName);
  }

  @Test
  void acceptsResolvableLedgerAndStepSelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "requirements.entries"),
            sel(ContextSourceKind.STEP_OUTPUT, "s1")),
        List.of(), null);
    WorkflowDefinition wf = workflow(
        List.of(step("s1"), stepWithSelection("s2", selection)),
        List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownLedgerSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void rejectsUnknownStepOutputSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "ghost")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown step 'ghost'");
  }

  @Test
  void rejectsUnknownArtifactSelector() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.ARTIFACT, "missing")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown artifact 'missing'");
  }

  @Test
  void skipsStateKeySelectors() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STATE_KEY, "some-key")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void acceptsContextPackSelectorInLoadedPackNames() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "coding-standards")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());
    ContextPack pack = pack("coding-standards", "full", "compact");

    assertThatCode(() -> validate(wf, Map.of("coding-standards", pack)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsContextPackSelectorNotInLoadedPackNames() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "unknown-pack")), List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());
    ContextPack pack = pack("coding-standards", "full", "compact");

    assertThatThrownBy(() -> validate(wf, Map.of("coding-standards", pack)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown context pack 'unknown-pack'");
  }

  @Test
  void rejectsFullVariantSelectorWhenPackDeclaresNoFullVariant() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "compact-only-pack", ContextVariant.FULL)),
        List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());
    ContextPack pack = pack("compact-only-pack", "compact");

    assertThatThrownBy(() -> validate(wf, Map.of("compact-only-pack", pack)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FULL")
        .hasMessageContaining("no 'full' variant");
  }

  @Test
  void rejectsCompactOnlyVariantSelectorWhenPackDeclaresNoCompactVariant() {
    // COMPACT_ONLY never falls back to full at resolution time, so a pack with only a full variant
    // must fail load-time validation, not fail closed on every run.
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "full-only-pack", ContextVariant.COMPACT_ONLY)),
        List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());
    ContextPack pack = pack("full-only-pack", "full");

    assertThatThrownBy(() -> validate(wf, Map.of("full-only-pack", pack)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("COMPACT_ONLY")
        .hasMessageContaining("no 'compact' variant");
  }

  @Test
  void acceptsCompactPreferredVariantSelectorWhenPackDeclaresOnlyFullVariant() {
    // COMPACT_PREFERRED falls back to full at resolution time when compact is absent — a pack with
    // only a full variant is a legitimate, resolvable combination, not a load-time failure.
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.CONTEXT_PACK, "full-only-pack",
            ContextVariant.COMPACT_PREFERRED)),
        List.of(), null);
    WorkflowDefinition wf = workflow(List.of(stepWithSelection("s1", selection)), List.of());
    ContextPack pack = pack("full-only-pack", "full");

    assertThatCode(() -> validate(wf, Map.of("full-only-pack", pack)))
        .doesNotThrowAnyException();
  }

  @Test
  void validatesExpandableScopeSelectors() {
    ContextSelection selection = new ContextSelection(List.of(),
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), null);
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

  @Test
  void rejectsDeterministicExtractOnANonLedgerSource() {
    // The shipped extractor only understands the ledger envelope shape; a non-LEDGER_SECTION
    // source must fail at load, not mid-run.
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.STATE_KEY, "some-key"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DETERMINISTIC_EXTRACT")
        .hasMessageContaining("STATE_KEY");
  }

  @Test
  void acceptsLlmSummaryCompactStepAtLoadTime() {
    // LLM_SUMMARY invokes its declared agentRef through the normal AgentInvoker path (Option 1,
    // design rev 6) — context-selection validation has nothing LLM_SUMMARY-specific to reject; the
    // agentRef itself is checked by validateAgentRefs (see WorkflowAgentRefCollectorTest), not here.
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsDuplicateLedgerIds() {
    WorkflowDefinition wf = workflow(List.of(step("s1")),
        List.of(ledger("requirements"), ledger("requirements")));

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate ledger id 'requirements'");
  }

  @Test
  void rejectsCompactStepSourceNamingALedgerSection() {
    // A section subpath resolves to a bare array the deterministic extractor cannot compact;
    // accepting it at load would let a run silently produce an empty compact form.
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(
            sel(ContextSourceKind.LEDGER_SECTION, "requirements.entries"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of(ledger("requirements")));

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whole ledger")
        .hasMessageContaining("requirements.entries");
  }

  @Test
  void acceptsLlmSummaryCompactStepOnALedgerSectionSubpath() {
    // The "must compact a whole ledger" restriction is specific to DETERMINISTIC_EXTRACT (the
    // shipped extractor only understands the whole envelope shape); LLM_SUMMARY's agent can
    // summarize a section just as well as a whole ledger, so a section subpath must be accepted.
    StepDefinition compact = StepDefinition.builder().withStepId("c").withName("c")
        .withBehaviour(new CompactBehaviour(
            sel(ContextSourceKind.LEDGER_SECTION, "requirements.entries"),
            new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(compact), List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsTwoCompactStepsTargetingTheIdenticalSourceWithDifferentModes() {
    // Compact siblings are keyed only by source id (kind+ref), not by producing step or mode/policy;
    // two COMPACT steps sharing a source with DIFFERENT configuration would collide silently at run
    // time (the second treated as "already up to date," its own mode never applied), so this must
    // fail at load instead.
    StepDefinition first = StepDefinition.builder().withStepId("c1").withName("c1")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    StepDefinition second = StepDefinition.builder().withStepId("c2").withName("c2")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new LlmSummary("STANDARD", "summarizer-agent"), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(first, second), List.of(ledger("requirements")));

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("c1")
        .hasMessageContaining("c2")
        .hasMessageContaining("LEDGER_SECTION:requirements");
  }

  @Test
  void acceptsTwoCompactStepsTargetingTheIdenticalSourceWithTheIdenticalModeAndPolicy() {
    // A legitimate, shipped pattern (see beh-compact.workflow): re-checking the same source with
    // identical configuration is safe — the second step correctly no-ops as UP_TO_DATE at run time
    // rather than colliding, since there's no configuration divergence to lose.
    StepDefinition first = StepDefinition.builder().withStepId("c1").withName("c1")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    StepDefinition second = StepDefinition.builder().withStepId("c2").withName("c2")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(first, second), List.of(ledger("requirements")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void acceptsTwoCompactStepsTargetingDifferentSources() {
    StepDefinition first = StepDefinition.builder().withStepId("c1").withName("c1")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "requirements"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    StepDefinition second = StepDefinition.builder().withStepId("c2").withName("c2")
        .withBehaviour(new CompactBehaviour(sel(ContextSourceKind.LEDGER_SECTION, "risks"),
            new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .build();
    WorkflowDefinition wf = workflow(List.of(first, second),
        List.of(ledger("requirements"), ledger("risks")));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void acceptsResolvableSelectorInsideBranchChild() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "branch-target-a")), List.of(), null);
    StepDefinition branchTargetA = step("branch-target-a");
    StepDefinition branchTargetB = stepWithSelection("branch-target-b", selection);
    BranchBehaviour branch = new BranchBehaviour("route",
        Map.of("a", branchTargetA, "b", branchTargetB), List.of(), null, false);
    StepDefinition branchStep = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(branch).build();
    WorkflowDefinition wf = workflow(List.of(branchStep), List.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownSelectorInsideBranchChild() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    StepDefinition branchTarget = stepWithSelection("branch-target", selection);
    BranchBehaviour branch = new BranchBehaviour("route", Map.of("a", branchTarget), List.of(),
        null, false);
    StepDefinition branchStep = StepDefinition.builder().withStepId("s1").withName("s1")
        .withBehaviour(branch).build();
    WorkflowDefinition wf = workflow(List.of(branchStep), List.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void acceptsResolvableSelectorInsideBlueprintRefSteps() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "bp-step-a")), List.of(), null);
    StepDefinition bpStepA = step("bp-step-a");
    StepDefinition bpStepB = stepWithSelection("bp-step-b", selection);
    BlueprintDefinition bp = blueprint("bp1", List.of(bpStepA, bpStepB));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp1")), List.of(),
        Map.of("bp1", bp));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownSelectorInsideBlueprintRefSteps() {
    ContextSelection selection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nope")), List.of(), null);
    BlueprintDefinition bp = blueprint("bp1", List.of(stepWithSelection("bp-step", selection)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp1")), List.of(),
        Map.of("bp1", bp));

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nope'");
  }

  @Test
  void aSelfReferentialBlueprintRefDoesNotOverflowTheStack() {
    // This walker stops descending once it revisits a blueprint already on the current path
    // (silently, since validateBlueprintRefs is the authoritative site that reports the cycle as
    // an error) rather than recursing until StackOverflowError.
    BlueprintDefinition bp = blueprint("bp1", List.of(new BlueprintRef("bp1")));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp1")), List.of(),
        Map.of("bp1", bp));

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void nestedWorkflowValidatesItsOwnLedgerScopeIndependently() {
    ContextSelection parentSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "parentLedger.entries")), List.of(), null);
    ContextSelection nestedSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nestedLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf",
        List.of(stepWithSelection("nested-step", nestedSelection)),
        List.of(ledger("nestedLedger")), Map.of());
    WorkflowDefinition wf = workflow("wf",
        List.of(stepWithSelection("parent-step", parentSelection), nestedWf),
        List.of(ledger("parentLedger")), Map.of());

    assertThatCode(() -> validate(wf)).doesNotThrowAnyException();
  }

  @Test
  void rejectsNestedWorkflowLedgerSelectorReferencedFromParentScope() {
    ContextSelection parentSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "nestedLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf", List.of(step("nested-step")),
        List.of(ledger("nestedLedger")), Map.of());
    WorkflowDefinition wf = workflow("wf",
        List.of(stepWithSelection("parent-step", parentSelection), nestedWf),
        List.of(ledger("parentLedger")), Map.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'nestedLedger'");
  }

  @Test
  void rejectsStepOutputSelectorReferencingAStepInsideANestedWorkflow() {
    // A nested WorkflowDefinition is a separate scope (see collectScopeStepIds); a STEP_OUTPUT
    // selector at the parent level must not resolve against a step id declared only inside it.
    ContextSelection parentSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.STEP_OUTPUT, "nested-step")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf", List.of(step("nested-step")), List.of(),
        Map.of());
    WorkflowDefinition wf = workflow("wf",
        List.of(stepWithSelection("parent-step", parentSelection), nestedWf), List.of(), Map.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("selects output of unknown step 'nested-step'");
  }

  @Test
  void rejectsParentWorkflowLedgerSelectorReferencedFromNestedScope() {
    ContextSelection nestedSelection = new ContextSelection(
        List.of(sel(ContextSourceKind.LEDGER_SECTION, "parentLedger.entries")), List.of(), null);
    WorkflowDefinition nestedWf = workflow("nested-wf",
        List.of(stepWithSelection("nested-step", nestedSelection)), List.of(), Map.of());
    WorkflowDefinition wf = workflow("wf", List.of(nestedWf), List.of(ledger("parentLedger")),
        Map.of());

    assertThatThrownBy(() -> validate(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown ledger 'parentLedger'");
  }
}
