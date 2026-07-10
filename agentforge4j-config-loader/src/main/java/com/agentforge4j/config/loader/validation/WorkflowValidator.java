// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector.AgentRefSite;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.reachability.AmbiguousStepId;
import com.agentforge4j.core.workflow.reachability.ReachableStepGraph;
import com.agentforge4j.core.workflow.reachability.WorkflowRefResolver;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.ContextSelection;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowValidator {

  private static final String CONTEXT_PACK_FULL_VARIANT = "full";
  private static final String CONTEXT_PACK_COMPACT_VARIANT = "compact";

  /**
   * Verifies that workflow references point to known workflows.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a workflow reference targets an unknown workflow
   */
  public void validateWorkflowRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values()
        .forEach(workflow -> walkForWorkflowRefExistence(workflow.steps(), workflow, workflows));
  }

  /**
   * Verifies that blueprint references point to declared blueprints in the same workflow.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a blueprint reference targets an unknown blueprint
   */
  public void validateBlueprintRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForBlueprintRefs(workflow.steps(), workflow,
        new LinkedHashSet<>()));
  }

  /**
   * Verifies that artifact references point to declared artifacts in the same workflow.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when an artifact reference targets an unknown artifact
   */
  public void validateArtifactRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForArtifactRefs(workflow.steps(), workflow));
  }

  /**
   * Verifies that workflow reference graphs do not contain cycles.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalStateException when a circular workflow reference is detected
   */
  public void validateCircularRefs(Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> adjacency = buildWorkflowRefAdjacency(workflows);
    detectWorkflowRefCycles(adjacency);
  }

  /**
   * Verifies that {@code RetryPreviousBehaviour} references target known steps.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a retry step reference targets an unknown step id
   */
  public void validateRetryStepRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> {
      Set<String> workflowStepIds = new HashSet<>();
      collectStepIds(workflow.steps(), workflowStepIds);
      walkForRetryStepRefs(workflow.steps(), workflow, workflowStepIds);
    });
  }

  /**
   * Verifies that, for every workflow treated as a run root, no step id is reachable from more than one structural
   * location across the reachable graph — the root, the blueprints it references, and the sub-workflows its
   * {@code WORKFLOW} steps reach. This is the exact graph the runtime resolves a step against at a gate or input
   * submission; rejecting the ambiguity here turns an otherwise mid-run {@link IllegalStateException} into a load-time
   * error.
   *
   * <p>Validation is per-root: the same id appearing in two <em>different</em> root workflows is fine
   * (they are separate runs), while a single sub-workflow or blueprint reached by more than one path within one root
   * collapses to a single location and is not ambiguous.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalStateException when a reachable step id resolves to two or more structural locations
   */
  public void validateReachableStepIdUniqueness(Map<String, WorkflowDefinition> workflows) {
    WorkflowRefResolver resolver = workflows::get;
    for (WorkflowDefinition root : workflows.values()) {
      List<AmbiguousStepId> ambiguous = ReachableStepGraph.findAmbiguousStepIds(root, resolver);
      Validate.isTrue(ambiguous.isEmpty(), () -> new IllegalStateException(
          ("Workflow '%s' has step ids reachable from multiple structural locations; reachable step "
              + "ids must be unique: %s").formatted(root.id(), describeAmbiguous(ambiguous))));
    }
  }

  private static String describeAmbiguous(List<AmbiguousStepId> ambiguous) {
    List<String> parts = new ArrayList<>();
    for (AmbiguousStepId entry : ambiguous) {
      parts.add("step id '%s' at %s".formatted(entry.stepId(), entry.locations()));
    }
    return String.join("; ", parts);
  }

  private void walkForWorkflowRefExistence(
      List<Executable> steps,
      WorkflowDefinition workflow,
      Map<String, WorkflowDefinition> workflows) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          assertWorkflowExists(wb.workflowRef(), step.stepId(), workflow.id(), workflows);
        } else if (step.behaviour() instanceof BranchBehaviour bb) {
          walkForWorkflowRefExistence(bb.childExecutables(), workflow, workflows);
        }
      } else if (executable instanceof BlueprintRef) {
        // No workflow refs to validate here.
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForWorkflowRefExistence(nested.steps(), nested, workflows);
      }
    }
  }

  private Map<String, List<String>> buildWorkflowRefAdjacency(
      Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> adjacency = new LinkedHashMap<>();
    for (WorkflowDefinition workflow : workflows.values()) {
      List<String> refs = new ArrayList<>();
      collectWorkflowRefs(workflow.steps(), refs);
      adjacency.put(workflow.id(), List.copyOf(refs));
    }
    return Map.copyOf(adjacency);
  }

  private void collectWorkflowRefs(List<Executable> steps, List<String> refs) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          refs.add(wb.workflowRef());
        } else if (step.behaviour() instanceof BranchBehaviour bb) {
          collectWorkflowRefs(bb.childExecutables(), refs);
        }
      } else if (executable instanceof BlueprintRef) {
        // No workflow refs to collect here.
      } else if (executable instanceof WorkflowDefinition nested) {
        collectWorkflowRefs(nested.steps(), refs);
      }
    }
  }

  private void detectWorkflowRefCycles(Map<String, List<String>> adjacency) {
    Set<String> visited = new HashSet<>();
    for (String workflowId : adjacency.keySet()) {
      if (!visited.contains(workflowId)) {
        dfsForCycle(workflowId, adjacency, visited, new HashSet<>(), new ArrayList<>());
      }
    }
  }

  private void dfsForCycle(
      String workflowId,
      Map<String, List<String>> adjacency,
      Set<String> visited,
      Set<String> inStack,
      List<String> path) {
    if (inStack.contains(workflowId)) {
      int cycleStart = path.indexOf(workflowId);
      List<String> cyclePath = new ArrayList<>(path.subList(cycleStart, path.size()));
      cyclePath.add(workflowId);
      throw new IllegalStateException(
          "Workflow '%s' has circular references: %s"
              .formatted(workflowId, String.join(" -> ", cyclePath)));
    }
    if (visited.contains(workflowId)) {
      return;
    }
    visited.add(workflowId);
    inStack.add(workflowId);
    path.add(workflowId);
    for (String ref : adjacency.getOrDefault(workflowId, List.of())) {
      if (adjacency.containsKey(ref)) {
        dfsForCycle(ref, adjacency, visited, inStack, path);
      }
    }
    path.remove(path.size() - 1);
    inStack.remove(workflowId);
  }

  // blueprintChain tracks blueprint ids on the CURRENT descent path (added on entry, removed on
  // backtrack) so a blueprint reachable from two sibling branches (a legitimate diamond) is not
  // mistaken for a cycle — only a blueprint that re-appears while still an ancestor of itself is
  // rejected. Without this, a self- or mutually-referential BlueprintRef would recurse until
  // StackOverflowError, which WorkflowDraftValidator's catch (RuntimeException) does not catch.
  private void walkForBlueprintRefs(List<Executable> steps, WorkflowDefinition workflow,
      Set<String> blueprintChain) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof BranchBehaviour branchBehaviour) {
          walkForBlueprintRefs(branchBehaviour.childExecutables(), workflow, blueprintChain);
        }
      } else if (executable instanceof BlueprintRef ref) {
        validateBlueprintExists(ref.blueprintId(), workflow);
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        Validate.notNull(blueprint,
            "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
                .formatted(workflow.id(), ref.blueprintId()));
        Validate.isTrue(blueprintChain.add(ref.blueprintId()),
            "Workflow '%s' contains a cyclic BlueprintRef chain reaching blueprint '%s' again: %s"
                .formatted(workflow.id(), ref.blueprintId(), blueprintChain));
        walkForBlueprintRefs(blueprint.steps(), workflow, blueprintChain);
        blueprintChain.remove(ref.blueprintId());
      } else if (executable instanceof WorkflowDefinition nested) {
        // Blueprint ids are a per-workflow namespace (workflow.blueprints()): a nested
        // WorkflowDefinition's own blueprint reusing an id already on the PARENT's descent path
        // is unrelated, not a cycle, so it descends with its own fresh chain — the same rule
        // walkScopeSelectors already follows via validateScopeSelectors.
        walkForBlueprintRefs(nested.steps(), nested, new LinkedHashSet<>());
      }
    }
  }

  /**
   * Verifies that every {@code VALIDATE} step's context-equality contracts reference an artifact in that step's own
   * {@code requiredArtifacts} allowlist. A contract pointing at a path outside the allowlist can never be satisfied at
   * runtime (that artifact is never captured for the step), so it is a config error caught here at load time.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a contract references an artifact outside the step's allowlist
   */
  public void validateValidateBehaviourContracts(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForValidateContracts(workflow.steps(), workflow,
        new LinkedHashSet<>()));
  }

  // See walkForBlueprintRefs's blueprintChain comment: a path-scoped (not global) visited set so a
  // legitimately diamond-shared blueprint is not rejected, only a true cycle.
  private void walkForValidateContracts(List<Executable> steps, WorkflowDefinition workflow,
      Set<String> blueprintChain) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof ValidateBehaviour validate) {
          assertContractsWithinAllowlist(validate, step.stepId(), workflow.id());
        } else if (step.behaviour() instanceof BranchBehaviour bb) {
          walkForValidateContracts(bb.childExecutables(), workflow, blueprintChain);
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && blueprintChain.add(ref.blueprintId())) {
          walkForValidateContracts(blueprint.steps(), workflow, blueprintChain);
          blueprintChain.remove(ref.blueprintId());
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        // See walkForBlueprintRefs's nested-WorkflowDefinition comment: a fresh chain, not the
        // parent's, since blueprint ids are a per-workflow namespace.
        walkForValidateContracts(nested.steps(), nested, new LinkedHashSet<>());
      }
    }
  }

  private static void assertContractsWithinAllowlist(ValidateBehaviour validate, String stepId,
      String workflowId) {
    List<String> allowlist = validate.requiredArtifacts();
    for (ContextEqualityContract contract : validate.contextEqualityContracts()) {
      Validate.isTrue(allowlist.contains(contract.artifactPath()),
          ("VALIDATE step '%s' in workflow '%s' has an equality contract on artifact '%s' which is not in its "
              + "requiredArtifacts allowlist %s")
              .formatted(stepId, workflowId, contract.artifactPath(), allowlist));
    }
  }

  private void walkForArtifactRefs(List<Executable> steps, WorkflowDefinition workflow) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof InputBehaviour ib) {
          assertArtifactExists(ib.artifactId(), step.stepId(), workflow.id(), workflow);
        } else if (step.behaviour() instanceof BranchBehaviour bb) {
          walkForArtifactRefs(bb.childExecutables(), workflow);
        }
      } else if (executable instanceof BlueprintRef) {
        // No artifact refs to validate here.
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForArtifactRefs(nested.steps(), nested);
      }
    }
  }

  private static void assertWorkflowExists(String workflowRef, String stepId, String workflowId,
      Map<String, WorkflowDefinition> workflows) {
    Validate.isTrue(workflows.containsKey(workflowRef),
        "Step '%s' in workflow '%s' references unknown workflow '%s'"
            .formatted(stepId, workflowId, workflowRef));
  }

  private static void validateBlueprintExists(String blueprintId, WorkflowDefinition workflow) {
    Validate.isTrue(workflow.blueprints().containsKey(blueprintId),
        "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
            .formatted(workflow.id(), blueprintId));
  }

  private static void assertArtifactExists(String artifactId, String stepId, String workflowId,
      WorkflowDefinition workflow) {
    Validate.isTrue(workflow.artifacts().containsKey(artifactId),
        "Step '%s' in workflow '%s' references unknown artifact '%s'"
            .formatted(stepId, workflowId, artifactId));
  }

  private static void collectStepIds(List<Executable> steps, Set<String> stepIds) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        stepIds.add(step.stepId());
      } else if (executable instanceof BlueprintRef) {
        // No step ids to collect here.
      } else if (executable instanceof WorkflowDefinition nested) {
        collectStepIds(nested.steps(), stepIds);
      }
    }
  }

  private static void walkForRetryStepRefs(List<Executable> steps,
      WorkflowDefinition workflow,
      Set<String> workflowStepIds) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof RetryPreviousBehaviour behaviour) {
          Validate.isTrue(workflowStepIds.contains(behaviour.retryStepId()),
              "RetryPreviousBehaviour in step '%s' of workflow '%s' references unknown step '%s'"
                  .formatted(step.stepId(), workflow.id(), behaviour.retryStepId()));
        }
      } else if (executable instanceof BlueprintRef) {
        // No retry refs to validate here.
      } else if (executable instanceof WorkflowDefinition nested) {
        Set<String> nestedStepIds = new HashSet<>();
        collectStepIds(nested.steps(), nestedStepIds);
        walkForRetryStepRefs(nested.steps(), nested, nestedStepIds);
      }
    }
  }

  /**
   * Verifies the structural integrity of each workflow's {@code requirements} declarations: requirement-id uniqueness,
   * that a targeted {@code stepId} resolves to a real step, and that no two requirements of the same type target the
   * same site. Requirement {@code type}, {@code action} values, and {@code default} payloads are opaque and are not
   * interpreted here.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a requirement declaration is structurally invalid
   */
  public void validateRequirements(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(WorkflowValidator::validateWorkflowRequirements);
  }

  private static void validateWorkflowRequirements(WorkflowDefinition workflow) {
    List<WorkflowRequirement> requirements = workflow.requirements();
    if (requirements.isEmpty()) {
      return;
    }
    Set<String> stepIds = new HashSet<>();
    collectStepIds(workflow.steps(), stepIds);
    Set<String> seenIds = new HashSet<>();
    Set<String> seenTargets = new HashSet<>();
    for (WorkflowRequirement requirement : requirements) {
      Validate.isTrue(seenIds.add(requirement.id()),
          "Workflow '%s' declares duplicate requirement id '%s'"
              .formatted(workflow.id(), requirement.id()));
      if (requirement.stepId() != null) {
        Validate.isTrue(stepIds.contains(requirement.stepId()),
            "Requirement '%s' in workflow '%s' targets unknown step '%s'"
                .formatted(requirement.id(), workflow.id(), requirement.stepId()));
      }
      String target = "%s|%s|%s|%s".formatted(requirement.type(), requirement.scope(),
          requirement.stepId(), requirement.action());
      Validate.isTrue(seenTargets.add(target),
          "Workflow '%s' declares conflicting requirements of type '%s' for the same target"
              .formatted(workflow.id(), requirement.type()));
    }
  }

  /**
   * Verifies that every agent reference in every workflow resolves to a known agent id.
   *
   * @param workflows workflows whose agent references are checked
   * @param agents    available agents keyed by id
   *
   * @throws UnresolvedAgentReferenceException when one or more agent references are unresolved
   */
  public void validateAgentRefs(
      Map<String, WorkflowDefinition> workflows,
      Map<String, AgentDefinition> agents) {
    List<String> missing = new ArrayList<>();
    for (WorkflowDefinition workflow : workflows.values()) {
      for (AgentRefSite site : WorkflowAgentRefCollector.collect(workflow)) {
        if (!agents.containsKey(site.agentRef())) {
          missing.add("workflow '%s' step '%s' references unknown agent '%s'"
              .formatted(site.resolvingWorkflowId(), site.stepId(), site.agentRef()));
        }
      }
    }
    Validate.isTrue(missing.isEmpty(), () -> new UnresolvedAgentReferenceException(missing));
  }

  /**
   * Verifies that every context selector a step declares — its {@code contextSelection} selectors and expandable
   * scope, and a {@code COMPACT} step's source — references something that exists: a {@code LEDGER_SECTION} names a
   * declared ledger, an {@code ARTIFACT} names a declared artifact, a {@code STEP_OUTPUT} names a real step, and a
   * {@code CONTEXT_PACK} names a pack in {@code loadedPacksByName} whose declared variants can actually satisfy the
   * selector's {@code ContextVariant} at resolution time — {@code FULL} requires a {@code "full"} variant,
   * {@code COMPACT_ONLY} requires a {@code "compact"} variant, {@code COMPACT_PREFERRED} requires either (its
   * fallback-to-full is legitimate). These are the same variant names and fallback rule
   * {@code ContextSourceResolver.resolveContextPack} applies at run time — a pack that passes here can never fail
   * closed there for a missing variant. {@code STATE_KEY} is unconstrained.
   *
   * @param workflows        workflows to validate
   * @param loadedPacksByName the context packs actually loaded for this assembly, keyed by name; empty when none are
   *                         configured (every {@code CONTEXT_PACK} selector then fails)
   *
   * @throws IllegalArgumentException when a selector references an unknown ledger, artifact, step, or pack, or a
   *                                  pack that cannot satisfy the selector's declared variant
   */
  public void validateContextSelectionRefs(Map<String, WorkflowDefinition> workflows,
      Map<String, ContextPack> loadedPacksByName) {
    workflows.values().forEach(
        workflow -> validateScopeSelectors(workflow, loadedPacksByName));
  }

  private static void validateScopeSelectors(WorkflowDefinition workflow,
      Map<String, ContextPack> loadedPacksByName) {
    Set<String> ledgerIds = new HashSet<>();
    for (LedgerDefinition ledger : workflow.ledgers()) {
      Validate.isTrue(ledgerIds.add(ledger.id()),
          "Workflow '%s' declares duplicate ledger id '%s'".formatted(workflow.id(), ledger.id()));
    }
    Set<String> artifactIds = workflow.artifacts().keySet();
    Set<String> stepIds = new HashSet<>();
    collectScopeStepIds(workflow.steps(), workflow, stepIds, new LinkedHashSet<>());
    walkScopeSelectors(workflow.steps(), workflow, ledgerIds, artifactIds, stepIds, loadedPacksByName,
        new LinkedHashSet<>());
  }

  // A second, deliberately different step-id collector from collectStepIds above: this one descends into
  // BranchBehaviour children and a BlueprintRef's referenced blueprint steps so a STEP_OUTPUT selector nested
  // inside either can resolve against sibling ids in the same reachable scope. collectStepIds (used by
  // validateRetryStepRefs/validateRequirements) does neither, since a retry target or requirement stepId is only
  // ever declared at the flat top level of a workflow's or blueprint's own steps() list. Do not merge the two —
  // they intentionally collect different scopes. blueprintChain is a path-scoped (not global) visited set —
  // see walkForBlueprintRefs's comment — so a diamond-shared blueprint is not mistaken for a cycle.
  private static void collectScopeStepIds(List<Executable> steps, WorkflowDefinition workflow,
      Set<String> stepIds, Set<String> blueprintChain) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        stepIds.add(step.stepId());
        if (step.behaviour() instanceof BranchBehaviour branch) {
          collectScopeStepIds(branch.childExecutables(), workflow, stepIds, blueprintChain);
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && blueprintChain.add(ref.blueprintId())) {
          collectScopeStepIds(blueprint.steps(), workflow, stepIds, blueprintChain);
          blueprintChain.remove(ref.blueprintId());
        }
      }
      // A nested WorkflowDefinition is a separate scope validated on its own.
    }
  }

  private static void walkScopeSelectors(List<Executable> steps, WorkflowDefinition workflow,
      Set<String> ledgerIds, Set<String> artifactIds, Set<String> stepIds,
      Map<String, ContextPack> loadedPacksByName, Set<String> blueprintChain) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        checkStepSelectors(step, workflow, ledgerIds, artifactIds, stepIds, loadedPacksByName);
        if (step.behaviour() instanceof BranchBehaviour branch) {
          walkScopeSelectors(branch.childExecutables(), workflow, ledgerIds, artifactIds, stepIds,
              loadedPacksByName, blueprintChain);
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && blueprintChain.add(ref.blueprintId())) {
          walkScopeSelectors(blueprint.steps(), workflow, ledgerIds, artifactIds, stepIds,
              loadedPacksByName, blueprintChain);
          blueprintChain.remove(ref.blueprintId());
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        validateScopeSelectors(nested, loadedPacksByName);
      }
    }
  }

  private static void checkStepSelectors(StepDefinition step, WorkflowDefinition workflow,
      Set<String> ledgerIds, Set<String> artifactIds, Set<String> stepIds,
      Map<String, ContextPack> loadedPacksByName) {
    ContextSelection selection = step.contextSelection();
    if (selection != null) {
      for (ContextSelector selector : selection.selectors()) {
        checkSelector(selector, step.stepId(), workflow, ledgerIds, artifactIds, stepIds,
            loadedPacksByName);
      }
      for (ContextSelector selector : selection.expandableScope()) {
        checkSelector(selector, step.stepId(), workflow, ledgerIds, artifactIds, stepIds,
            loadedPacksByName);
      }
    }
    if (step.behaviour() instanceof CompactBehaviour compact) {
      checkSelector(compact.source(), step.stepId(), workflow, ledgerIds, artifactIds, stepIds,
          loadedPacksByName);
      if (compact.mode() instanceof DeterministicExtract) {
        // The shipped extractor only understands the ledger envelope shape — reject other source
        // kinds at load rather than failing mid-run, matching the fail-early rule below.
        Validate.isTrue(compact.source().kind() == ContextSourceKind.LEDGER_SECTION,
            ("COMPACT step '%s' in workflow '%s' declares DETERMINISTIC_EXTRACT, which is only "
                + "implemented for LEDGER_SECTION sources; source kind is %s")
                .formatted(step.stepId(), workflow.id(), compact.source().kind()));
      }
      // LLM_SUMMARY's agentRef is validated like any other agent reference — see
      // WorkflowAgentRefCollector's CompactBehaviour/LlmSummary branch, checked by validateAgentRefs.
      // A deterministic extract operates on the whole ledger envelope; a section subpath resolves
      // to a bare array, which the extractor cannot compact — reject it here rather than letting a
      // run produce an empty compact form of a non-empty ledger.
      if (compact.source().kind() == ContextSourceKind.LEDGER_SECTION) {
        Validate.isTrue(!compact.source().ref().contains("."),
            "COMPACT step '%s' in workflow '%s' must compact a whole ledger; source '%s' names a ledger section"
                .formatted(step.stepId(), workflow.id(), compact.source().ref()));
      }
    }
  }

  private static void checkSelector(ContextSelector selector, String stepId,
      WorkflowDefinition workflow, Set<String> ledgerIds, Set<String> artifactIds,
      Set<String> stepIds, Map<String, ContextPack> loadedPacksByName) {
    ContextSourceKind kind = selector.kind();
    if (kind == ContextSourceKind.LEDGER_SECTION) {
      String ledgerId = ledgerId(selector.ref());
      Validate.isTrue(ledgerIds.contains(ledgerId),
          "Step '%s' in workflow '%s' selects unknown ledger '%s'"
              .formatted(stepId, workflow.id(), ledgerId));
    } else if (kind == ContextSourceKind.ARTIFACT) {
      Validate.isTrue(artifactIds.contains(selector.ref()),
          "Step '%s' in workflow '%s' selects unknown artifact '%s'"
              .formatted(stepId, workflow.id(), selector.ref()));
    } else if (kind == ContextSourceKind.STEP_OUTPUT) {
      Validate.isTrue(stepIds.contains(selector.ref()),
          "Step '%s' in workflow '%s' selects output of unknown step '%s'"
              .formatted(stepId, workflow.id(), selector.ref()));
    } else if (kind == ContextSourceKind.CONTEXT_PACK) {
      checkContextPackSelector(selector, stepId, workflow, loadedPacksByName);
    }
    // STATE_KEY is unconstrained.
  }

  /**
   * Same {@code "full"}/{@code "compact"} variant names and fallback rule as
   * {@code ContextSourceResolver.resolveContextPack} (runtime module; duplicated here rather than shared, since
   * config-loader does not depend on runtime): {@code FULL} requires {@code "full"}; {@code COMPACT_ONLY} requires
   * {@code "compact"} with no fallback; {@code COMPACT_PREFERRED} requires either (it falls back to {@code "full"}
   * at resolution time when {@code "compact"} is absent).
   */
  private static void checkContextPackSelector(ContextSelector selector, String stepId,
      WorkflowDefinition workflow, Map<String, ContextPack> loadedPacksByName) {
    ContextPack pack = loadedPacksByName.get(selector.ref());
    Validate.isTrue(pack != null,
        "Step '%s' in workflow '%s' selects unknown context pack '%s'"
            .formatted(stepId, workflow.id(), selector.ref()));
    boolean hasFull = pack.variants().containsKey(CONTEXT_PACK_FULL_VARIANT);
    boolean hasCompact = pack.variants().containsKey(CONTEXT_PACK_COMPACT_VARIANT);
    if (selector.variant() == ContextVariant.FULL) {
      Validate.isTrue(hasFull,
          ("Step '%s' in workflow '%s' selects context pack '%s' as FULL, but the pack declares no "
              + "'%s' variant").formatted(stepId, workflow.id(), selector.ref(),
              CONTEXT_PACK_FULL_VARIANT));
    } else if (selector.variant() == ContextVariant.COMPACT_ONLY) {
      Validate.isTrue(hasCompact,
          ("Step '%s' in workflow '%s' selects context pack '%s' as COMPACT_ONLY, but the pack "
              + "declares no '%s' variant (COMPACT_ONLY never falls back to '%s')").formatted(
              stepId, workflow.id(), selector.ref(), CONTEXT_PACK_COMPACT_VARIANT,
              CONTEXT_PACK_FULL_VARIANT));
    } else {
      Validate.isTrue(hasFull || hasCompact,
          ("Step '%s' in workflow '%s' selects context pack '%s' as COMPACT_PREFERRED, but the "
              + "pack declares neither a '%s' nor a '%s' variant").formatted(stepId, workflow.id(),
              selector.ref(), CONTEXT_PACK_COMPACT_VARIANT, CONTEXT_PACK_FULL_VARIANT));
    }
  }

  private static String ledgerId(String ref) {
    int dot = ref.indexOf('.');
    return dot < 0 ? ref : ref.substring(0, dot);
  }
}
