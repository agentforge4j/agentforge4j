// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.core.spi.governance.TokenGovernanceSignal;
import com.agentforge4j.core.spi.governance.WasteSignalKind;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.runtime.context.CanonicalJson;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, syntactic waste-signal evaluator. Every method is a pure function over
 * explicitly-passed fingerprints/coordinates — it observes nothing itself. Callers are responsible for
 * computing fingerprints (see {@link com.agentforge4j.runtime.context.ContextFingerprint}) and for
 * deciding what "prior" values to compare against.
 *
 * <p>{@link WasteSignalKind#REDUNDANT_COMPACTION} has no evaluator method here: it is always caught by
 * {@code CompactBehaviourHandler}'s own {@code UP_TO_DATE} skip rule before a detector would observe it
 * (see that class and {@link WasteSignalKind#REDUNDANT_COMPACTION}'s Javadoc).
 *
 * <p><strong>Wired into the runtime.</strong> {@code AgentInvoker} calls
 * {@link #evaluateDuplicateInvocation} and {@link #evaluateUnjustifiedTierEscalation} against a
 * per-step "prior invocation fingerprint" record it persists on {@code WorkflowState} (see
 * {@code WasteDetectorHistoryStore}); {@code AbstractLoopStrategy} calls
 * {@link #evaluateUnchangedLoopContext} and {@link #evaluateRepeatedLoopOutput}, shared by all four
 * loop strategies ({@code FixedCountLoopStrategy}, {@code ForEachLoopStrategy},
 * {@code AgentSignalLoopStrategy}, {@code EvaluatorLoopStrategy}), against the same persisted-history
 * mechanism keyed per loop blueprint. Both call sites record a raised signal as a
 * {@code TOKEN_GOVERNANCE_SIGNAL} audit event and notify the configured
 * {@link com.agentforge4j.core.spi.governance.WasteSignalPolicy}. {@link #evaluateOverbroadContext}
 * is the one evaluator with no production caller yet: it needs a load-time call site with both a
 * {@link WorkflowDefinition} and a {@link StepDefinition} in scope together, which neither wired
 * call site has — independently tested and ready to wire when such a call site exists.
 */
public final class WasteDetector {

  private WasteDetector() {
  }

  /**
   * Evaluates {@link WasteSignalKind#DUPLICATE_INVOCATION}: the same agent invoked with the same
   * scoped-context and input fingerprints as its immediately prior invocation of this step, on a
   * non-retry path.
   *
   * @param stepId                        the step being invoked; non-blank
   * @param agentId                       the agent being invoked; non-blank
   * @param scopedContextFingerprint      fingerprint of the context this invocation resolved; non-blank
   * @param inputFingerprint              fingerprint of this invocation's rendered input; non-blank
   * @param priorScopedContextFingerprint the prior invocation's context fingerprint, or {@code null}
   *                                      when there is no prior invocation to compare against
   * @param priorInputFingerprint         the prior invocation's input fingerprint, or {@code null}
   * @param isRetry                       whether this invocation is a retry of the prior one (retries
   *                                      are expected to be identical and are never flagged)
   *
   * @return the signal, or empty when it does not apply
   */
  public static Optional<TokenGovernanceSignal> evaluateDuplicateInvocation(
      String stepId, String agentId, String scopedContextFingerprint, String inputFingerprint,
      String priorScopedContextFingerprint, String priorInputFingerprint, boolean isRetry) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notBlank(scopedContextFingerprint, "scopedContextFingerprint must not be blank");
    Validate.notBlank(inputFingerprint, "inputFingerprint must not be blank");
    if (isRetry || priorScopedContextFingerprint == null || priorInputFingerprint == null) {
      return Optional.empty();
    }
    if (scopedContextFingerprint.equals(priorScopedContextFingerprint)
        && inputFingerprint.equals(priorInputFingerprint)) {
      return Optional.of(new TokenGovernanceSignal(WasteSignalKind.DUPLICATE_INVOCATION, stepId,
          agentId, "contextFingerprint=%s inputFingerprint=%s".formatted(scopedContextFingerprint,
              inputFingerprint)));
    }
    return Optional.empty();
  }

  /**
   * Evaluates {@link WasteSignalKind#UNJUSTIFIED_TIER_ESCALATION}: the resolved tier increased
   * relative to a prior invocation of the same step whose input and context fingerprints are
   * unchanged.
   *
   * @param stepId                   the step being invoked; non-blank
   * @param agentId                  the agent being invoked; non-blank
   * @param resolvedTier             this invocation's resolved tier; non-{@code null}
   * @param priorResolvedTier        the prior invocation's resolved tier, or {@code null} when there
   *                                 is no prior invocation
   * @param contextFingerprint       this invocation's context fingerprint; non-blank
   * @param priorContextFingerprint  the prior invocation's context fingerprint, or {@code null}
   * @param inputFingerprint         this invocation's input fingerprint; non-blank
   * @param priorInputFingerprint    the prior invocation's input fingerprint, or {@code null}
   *
   * @return the signal, or empty when it does not apply
   */
  public static Optional<TokenGovernanceSignal> evaluateUnjustifiedTierEscalation(
      String stepId, String agentId, ModelTier resolvedTier, ModelTier priorResolvedTier,
      String contextFingerprint, String priorContextFingerprint, String inputFingerprint,
      String priorInputFingerprint) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(resolvedTier, "resolvedTier must not be null");
    Validate.notBlank(contextFingerprint, "contextFingerprint must not be blank");
    Validate.notBlank(inputFingerprint, "inputFingerprint must not be blank");
    if (priorResolvedTier == null || priorContextFingerprint == null
        || priorInputFingerprint == null) {
      return Optional.empty();
    }
    boolean tierIncreased = resolvedTier.ordinal() > priorResolvedTier.ordinal();
    boolean unchangedInputs = contextFingerprint.equals(priorContextFingerprint)
        && inputFingerprint.equals(priorInputFingerprint);
    if (tierIncreased && unchangedInputs) {
      return Optional.of(new TokenGovernanceSignal(WasteSignalKind.UNJUSTIFIED_TIER_ESCALATION,
          stepId, agentId, "priorTier=%s resolvedTier=%s".formatted(priorResolvedTier,
              resolvedTier)));
    }
    return Optional.empty();
  }

  /**
   * Evaluates {@link WasteSignalKind#UNCHANGED_LOOP_CONTEXT}: iteration {@code N}'s context
   * fingerprint equals iteration {@code N-1}'s.
   *
   * @param stepId                        the loop-body step whose context is being evaluated;
   *                                       non-blank
   * @param blueprintId                   the enclosing blueprint id; non-blank
   * @param iteration                     the 1-based iteration index; must be positive
   * @param contextFingerprint            this iteration's context fingerprint; non-blank
   * @param priorIterationContextFingerprint the previous iteration's context fingerprint, or
   *                                       {@code null} on the first iteration
   *
   * @return the signal, or empty when it does not apply
   */
  public static Optional<TokenGovernanceSignal> evaluateUnchangedLoopContext(String stepId,
      String blueprintId, int iteration, String contextFingerprint,
      String priorIterationContextFingerprint) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.isGreaterThanZero(iteration, "iteration must be positive");
    Validate.notBlank(contextFingerprint, "contextFingerprint must not be blank");
    if (priorIterationContextFingerprint == null
        || !contextFingerprint.equals(priorIterationContextFingerprint)) {
      return Optional.empty();
    }
    return Optional.of(new TokenGovernanceSignal(WasteSignalKind.UNCHANGED_LOOP_CONTEXT, stepId,
        null, "blueprintId=%s iteration=%d contextFingerprint=%s".formatted(blueprintId, iteration,
            contextFingerprint)));
  }

  /**
   * Evaluates {@link WasteSignalKind#REPEATED_LOOP_OUTPUT}: this iteration's normalized output
   * fingerprint repeats one already seen in an earlier iteration.
   *
   * @param stepId                          the loop-body step whose output is being evaluated;
   *                                         non-blank
   * @param blueprintId                     the enclosing blueprint id; non-blank
   * @param iteration                       the 1-based iteration index; must be positive
   * @param normalizedOutputFingerprint     this iteration's normalized-output fingerprint; non-blank
   * @param priorIterationOutputFingerprints fingerprints already seen in earlier iterations; must not
   *                                         be {@code null} (empty on the first iteration)
   *
   * @return the signal, or empty when it does not apply
   */
  public static Optional<TokenGovernanceSignal> evaluateRepeatedLoopOutput(String stepId,
      String blueprintId, int iteration, String normalizedOutputFingerprint,
      Set<String> priorIterationOutputFingerprints) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.isGreaterThanZero(iteration, "iteration must be positive");
    Validate.notBlank(normalizedOutputFingerprint, "normalizedOutputFingerprint must not be blank");
    Validate.notNull(priorIterationOutputFingerprints,
        "priorIterationOutputFingerprints must not be null");
    if (!priorIterationOutputFingerprints.contains(normalizedOutputFingerprint)) {
      return Optional.empty();
    }
    return Optional.of(new TokenGovernanceSignal(WasteSignalKind.REPEATED_LOOP_OUTPUT, stepId, null,
        "blueprintId=%s iteration=%d outputFingerprint=%s".formatted(blueprintId, iteration,
            normalizedOutputFingerprint)));
  }

  /**
   * Evaluates {@link WasteSignalKind#OVERBROAD_CONTEXT}: {@code step} declares no
   * {@code contextSelection} in a workflow where at least one other step does.
   *
   * @param workflow the enclosing workflow; must not be {@code null}
   * @param step     the step to evaluate; must not be {@code null}
   *
   * @return the signal, or empty when it does not apply
   */
  public static Optional<TokenGovernanceSignal> evaluateOverbroadContext(WorkflowDefinition workflow,
      StepDefinition step) {
    Validate.notNull(workflow, "workflow must not be null");
    Validate.notNull(step, "step must not be null");
    if (step.contextSelection() != null) {
      return Optional.empty();
    }
    if (!anyStepDeclaresContextSelection(workflow.steps(), workflow)) {
      return Optional.empty();
    }
    return Optional.of(new TokenGovernanceSignal(WasteSignalKind.OVERBROAD_CONTEXT, step.stepId(),
        null, "workflow '%s' declares contextSelection on at least one other step".formatted(
            workflow.id())));
  }

  private static boolean anyStepDeclaresContextSelection(List<Executable> executables,
      WorkflowDefinition workflow) {
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step) {
        if (step.contextSelection() != null) {
          return true;
        }
        if (step.behaviour() instanceof BranchBehaviour branch
            && anyStepDeclaresContextSelection(branch.childExecutables(), workflow)) {
          return true;
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && anyStepDeclaresContextSelection(blueprint.steps(), workflow)) {
          return true;
        }
      }
      // A nested WorkflowDefinition is a separate scope evaluated on its own.
    }
    return false;
  }

  /**
   * Normalizes raw agent output for stable fingerprinting: canonical JSON (sorted
   * keys) when {@code rawOutput} parses as JSON, otherwise whitespace-collapsed text.
   *
   * @param rawOutput the raw output to normalize; must not be {@code null}
   * @param mapper    used to attempt JSON parsing and canonical rendering; must not be {@code null}
   *
   * @return the normalized text; never {@code null}
   */
  public static String normalizeOutput(String rawOutput, ObjectMapper mapper) {
    Validate.notNull(rawOutput, "rawOutput must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    try {
      JsonNode parsed = mapper.readTree(rawOutput);
      if (parsed != null && (parsed.isObject() || parsed.isArray())) {
        return CanonicalJson.render(parsed, mapper);
      }
    } catch (Exception ignored) {
      // Not parseable JSON; fall through to whitespace collapsing.
    }
    return rawOutput.strip().replaceAll("\\s+", " ");
  }
}
