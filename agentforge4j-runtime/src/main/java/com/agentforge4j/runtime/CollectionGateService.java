// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.runtime.CloseRequest;
import com.agentforge4j.core.runtime.CloseResult;
import com.agentforge4j.core.runtime.CollectionSubmission;
import com.agentforge4j.core.runtime.CollectionView;
import com.agentforge4j.core.runtime.SubmissionResult;
import com.agentforge4j.core.spi.validation.CollectionItemSchemaValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.collection.CollectionAction;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizationException;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizer;
import com.agentforge4j.core.workflow.collection.CollectionItem;
import com.agentforge4j.core.workflow.collection.CollectionPayload;
import com.agentforge4j.core.workflow.collection.CollectionPhase;
import com.agentforge4j.core.workflow.collection.CollectionState;
import com.agentforge4j.core.workflow.collection.CollectionSubmissionContext;
import com.agentforge4j.core.workflow.collection.CollectionSubmissionValidator;
import com.agentforge4j.core.workflow.collection.Decision;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.FileRef;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionContext;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates collection-gate mutation, authorization, and audit. Mutates the supplied {@link WorkflowState} in place;
 * persistence and the post-close drive stay with {@link DefaultWorkflowRuntime}. Not thread-safe on its own —
 * collection gates are naturally multi-actor, so {@link DefaultWorkflowRuntime} serialises calls into this service per
 * run under a dedicated lock (design §9's "run's state-write lock"), distinct from the rest of the runtime's
 * caller-serialized drive contract.
 */
final class CollectionGateService {

  private static final System.Logger LOG = System.getLogger(CollectionGateService.class.getName());

  private final EventRecorder eventRecorder;
  private final Clock clock;
  private final RequirementResolver requirementResolver;
  private final CollectionAuthorizer collectionAuthorizer;
  private final CollectionItemSchemaValidator itemSchemaValidator;
  private final CollectionSubmissionValidator submissionValidator;
  private final ObjectMapper objectMapper;

  CollectionGateService(EventRecorder eventRecorder, Clock clock,
      RequirementResolver requirementResolver, CollectionAuthorizer collectionAuthorizer,
      CollectionItemSchemaValidator itemSchemaValidator,
      CollectionSubmissionValidator submissionValidator,
      ObjectMapper objectMapper) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.requirementResolver =
        Validate.notNull(requirementResolver, "requirementResolver must not be null");
    this.collectionAuthorizer =
        Validate.notNull(collectionAuthorizer, "collectionAuthorizer must not be null");
    this.itemSchemaValidator =
        Validate.notNull(itemSchemaValidator, "itemSchemaValidator must not be null");
    this.submissionValidator =
        Validate.notNull(submissionValidator, "submissionValidator must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
  }

  // ---- submit -------------------------------------------------------------------------------

  SubmissionResult submit(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, CollectionSubmission submission, String actorId) {
    CollectionState gate = requireOpenGate(state, stepId);
    authorize(state, workflow, cfg, stepId, CollectionAction.SUBMIT, actorId);

    // Under REJECT_BY_CLIENT_TOKEN a repeated token is a hard duplicate refusal; the silent
    // idempotent return applies to the other policies only (a retry is indistinguishable from a
    // duplicate, and the workflow author chose refusal).
    if (cfg.duplicatePolicy() == DuplicatePolicy.REJECT_BY_CLIENT_TOKEN
        && clientTokenSeen(gate, submission.clientToken())) {
      emitRejected(state, stepId, actorId, CollectionAction.SUBMIT, "DUPLICATE");
      return new SubmissionResult(SubmissionResult.Status.REJECTED, null, 0, "DUPLICATE");
    }
    SubmissionResult idempotent = idempotentReturn(gate, submission.clientToken());
    if (idempotent != null) {
      return idempotent;
    }
    String rejection = checkSubmitConstraints(gate, cfg, submission, actorId);
    if (rejection == null) {
      rejection = checkItemSchema(cfg, submission.payload());
    }
    if (rejection == null) {
      rejection = checkSubmissionValidator(state, workflow, cfg, stepId, gate, submission, actorId,
          null);
    }
    if (rejection != null) {
      emitRejected(state, stepId, actorId, CollectionAction.SUBMIT, rejection);
      return new SubmissionResult(SubmissionResult.Status.REJECTED, null, 0, rejection);
    }

    String submissionId = UUID.randomUUID().toString();
    CollectionItem item = new CollectionItem(submissionId, actorId, clock.instant(), 1, false,
        submission.payload(), submission.dedupeKey(), submission.clientToken());
    List<CollectionItem> items = new ArrayList<>(gate.items());
    items.add(item);
    storeGate(state, withItemsAndSeenToken(gate, items, submission.clientToken()));
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_ITEM_SUBMITTED,
        toJson(submittedPayload(submissionId, actorId, item)), actorId);
    return new SubmissionResult(SubmissionResult.Status.ACCEPTED, submissionId, 1, null);
  }

  // ---- replace ------------------------------------------------------------------------------

  SubmissionResult replace(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, String submissionId, CollectionSubmission replacement, String actorId) {
    Validate.notBlank(submissionId, "submissionId must not be blank");
    CollectionState gate = requireOpenGate(state, stepId);
    CollectionItem existing = requireLiveItem(gate, submissionId);
    boolean owns = existing.submittedByActorId().equals(actorId);
    CollectionAction action = resolveReplaceAction(cfg.replacementPolicy(), owns, stepId);
    authorize(state, workflow, cfg, stepId, action, actorId);

    if (cfg.duplicatePolicy() == DuplicatePolicy.REJECT_BY_CLIENT_TOKEN
        && clientTokenSeen(gate, replacement.clientToken())) {
      emitRejected(state, stepId, actorId, action, "DUPLICATE");
      return new SubmissionResult(SubmissionResult.Status.REJECTED, null, 0, "DUPLICATE");
    }
    SubmissionResult idempotent = idempotentReturn(gate, replacement.clientToken());
    if (idempotent != null) {
      return idempotent;
    }
    String rejection = checkReplaceConstraints(gate, cfg, submissionId, replacement);
    if (rejection == null) {
      rejection = checkItemSchema(cfg, replacement.payload());
    }
    if (rejection == null) {
      rejection = checkSubmissionValidator(state, workflow, cfg, stepId, gate, replacement, actorId,
          submissionId);
    }
    if (rejection != null) {
      emitRejected(state, stepId, actorId, action, rejection);
      return new SubmissionResult(SubmissionResult.Status.REJECTED, null, 0, rejection);
    }

    // The replacement item records the replacing actor and timestamp for this version; ownership for
    // subsequent owner-scoped operations follows the latest version, not the original submitter.
    int newVersion = existing.version() + 1;
    CollectionItem replaced = new CollectionItem(submissionId, actorId, clock.instant(), newVersion,
        false, replacement.payload(), replacement.dedupeKey(), replacement.clientToken());
    List<CollectionItem> items = replaceSlot(gate.items(), submissionId, replaced);
    storeGate(state, withItemsAndSeenToken(gate, items, replacement.clientToken()));
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("submissionId", submissionId);
    payload.put("version", newVersion);
    payload.put("actorId", actorId);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_ITEM_REPLACED,
        toJson(payload), actorId);
    return new SubmissionResult(SubmissionResult.Status.ACCEPTED, submissionId, newVersion, null);
  }

  // ---- withdraw -----------------------------------------------------------------------------

  void withdraw(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, String submissionId, String actorId) {
    Validate.notBlank(submissionId, "submissionId must not be blank");
    CollectionState gate = requireOpenGate(state, stepId);
    CollectionItem existing = requireLiveItem(gate, submissionId);
    boolean owns = existing.submittedByActorId().equals(actorId);
    CollectionAction action = resolveWithdrawAction(cfg.withdrawalPolicy(), owns, stepId);
    authorize(state, workflow, cfg, stepId, action, actorId);

    CollectionItem withdrawn = new CollectionItem(submissionId, existing.submittedByActorId(),
        existing.submittedAt(), existing.version(), true, existing.payload(), existing.dedupeKey(),
        existing.clientToken());
    List<CollectionItem> items = replaceSlot(gate.items(), submissionId, withdrawn);
    storeGate(state, withItemsAndSeenToken(gate, items, null));
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("submissionId", submissionId);
    payload.put("actorId", actorId);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_ITEM_WITHDRAWN,
        toJson(payload), actorId);
  }

  // ---- close --------------------------------------------------------------------------------

  CloseResult close(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, CloseRequest request, List<String> outputKeys) {
    CollectionState gate = requireGate(state, stepId);
    authorize(state, workflow, cfg, stepId, CollectionAction.CLOSE, request.actorId());
    if (request.override()) {
      authorize(state, workflow, cfg, stepId, CollectionAction.OVERRIDE, request.actorId());
    }
    if (request.reason() == CloseReason.DEADLINE) {
      // A DEADLINE reason carries real security semantics -- it can satisfy externalDeadlineClosable
      // even when manualClose is false -- so it needs its own authorization independent of the
      // generic CLOSE check above, which any actor otherwise passes under OPEN mode.
      authorize(state, workflow, cfg, stepId, CollectionAction.DEADLINE_CLOSE, request.actorId());
    }

    // Idempotent no-op: already closed, or this close token was already accepted.
    if (gate.phase() == CollectionPhase.CLOSED
        || (request.closeToken() != null && gate.seenCloseTokens().contains(request.closeToken()))) {
      return new CloseResult(true, false, liveItems(gate).size(), null);
    }

    ObjectNode closeRequested = objectMapper.createObjectNode();
    closeRequested.put("actorId", request.actorId());
    closeRequested.put("reason", request.reason().name());
    // A DEADLINE-reasoned request gets its own, directly filterable event type instead of making
    // every consumer parse the generic event's reason field to distinguish an external deadline
    // trigger from a human-requested close.
    WorkflowEventType closeRequestedType = request.reason() == CloseReason.DEADLINE
        ? WorkflowEventType.COLLECTION_DEADLINE_CLOSE_REQUESTED
        : WorkflowEventType.COLLECTION_CLOSE_REQUESTED;
    eventRecorder.record(state.getRunId(), stepId, closeRequestedType,
        toJson(closeRequested), request.actorId());

    String notClosable = checkClosable(cfg, request.reason());
    if (notClosable != null) {
      emitCloseRejected(state, stepId, request.actorId(), notClosable, liveItems(gate).size(),
          cfg.minItems());
      return new CloseResult(false, false, 0, notClosable);
    }

    int liveCount = liveItems(gate).size();
    boolean minUnmet = liveCount < cfg.minItems();
    if (minUnmet && !request.override()) {
      String reason = "minimum %d items required, have %d".formatted(cfg.minItems(), liveCount);
      emitCloseRejected(state, stepId, request.actorId(), reason, liveCount, cfg.minItems());
      return new CloseResult(false, false, 0, reason);
    }

    CloseReason effectiveReason = minUnmet ? CloseReason.OVERRIDE : request.reason();
    boolean advance = cfg.reopenPolicy() == ReopenPolicy.NONE;
    storeGate(state, closedGate(gate, request, effectiveReason));
    ObjectNode closed = objectMapper.createObjectNode();
    closed.put("actorId", request.actorId());
    closed.put("reason", effectiveReason.name());
    closed.put("finalCount", liveCount);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_CLOSED,
        toJson(closed), request.actorId());

    if (advance) {
      publishAndComplete(state, stepId, outputKeys);
    }
    return new CloseResult(true, advance, liveCount, null);
  }

  // ---- reopen -------------------------------------------------------------------------------

  void reopen(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, String actorId) {
    CollectionState gate = requireGate(state, stepId);
    authorize(state, workflow, cfg, stepId, CollectionAction.REOPEN, actorId);
    Validate.isTrue(cfg.reopenPolicy() == ReopenPolicy.ALLOWED,
        "Reopen is not permitted for collection step '%s'".formatted(stepId));
    Validate.isTrue(gate.phase() == CollectionPhase.CLOSED,
        "Collection step '%s' is not closed".formatted(stepId));
    storeGate(state, reopenedGate(gate));
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("actorId", actorId);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_REOPENED,
        toJson(payload), actorId);
  }

  // ---- view ---------------------------------------------------------------------------------

  CollectionView view(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, String actorId) {
    CollectionState gate = requireGate(state, stepId);
    authorize(state, workflow, cfg, stepId, CollectionAction.VIEW, actorId);
    List<CollectionItem> live = liveItems(gate);
    return new CollectionView(stepId, gate.phase(), live, live.size(), gate.closeReason());
  }

  // ---- advance (continueRun on an ALLOWED-reopen gate that was closed) -----------------------

  void advanceClosed(WorkflowState state, String stepId, List<String> outputKeys) {
    CollectionState gate = requireGate(state, stepId);
    Validate.isTrue(gate.phase() == CollectionPhase.CLOSED,
        "Collection step '%s' is still open; close it before continuing".formatted(stepId));
    publishAndComplete(state, stepId, outputKeys);
  }

  // ---- helpers ------------------------------------------------------------------------------

  private void publishAndComplete(WorkflowState state, String stepId, List<String> outputKeys) {
    List<CollectionItem> materialized = liveItems(requireGate(state, stepId));
    String json = serialize(materialized);
    for (String key : outputKeys) {
      state.putContextValue(key, new JsonContextValue(json, ContextProvenance.USER_SUPPLIED));
    }
    state.putStepOutput(stepId, "collected");
    state.setStatus(WorkflowStatus.RUNNING);
    state.setLastUpdatedAt(clock.instant());
  }

  /**
   * Serialises the materialized collection to a JSON array using the node API only, so it does not depend on a
   * Java-time Jackson module being registered. Inline JSON is embedded as real JSON when parseable, otherwise as a text
   * node; timestamps are ISO-8601 strings.
   */
  private String serialize(List<CollectionItem> items) {
    ArrayNode array = objectMapper.createArrayNode();
    for (CollectionItem item : items) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("submissionId", item.submissionId());
      node.put("submittedByActorId", item.submittedByActorId());
      node.put("submittedAt", item.submittedAt().toString());
      node.put("version", item.version());
      CollectionPayload payload = item.payload();
      if (payload.inlineJson() != null) {
        node.set("inline", parseOrText(payload.inlineJson()));
      }
      ArrayNode files = node.putArray("files");
      for (FileRef ref : payload.files()) {
        ObjectNode fileNode = files.addObject();
        fileNode.put("path", ref.path());
        fileNode.put("filename", ref.filename());
        fileNode.put("contentType", ref.contentType());
        fileNode.put("sizeBytes", ref.sizeBytes());
      }
      array.add(node);
    }
    try {
      return objectMapper.writeValueAsString(array);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize materialized collection", ex);
    }
  }

  private JsonNode parseOrText(String inlineJson) {
    try {
      return objectMapper.readTree(inlineJson);
    } catch (JsonProcessingException ex) {
      return objectMapper.getNodeFactory().textNode(inlineJson);
    }
  }

  /**
   * Serialises an audit/event payload built via the node API, so actor-supplied content (actor ids, denial reasons) is
   * properly JSON-escaped rather than interpolated into a hand-written literal.
   */
  private String toJson(ObjectNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize collection audit payload", ex);
    }
  }

  private void authorize(WorkflowState state, WorkflowDefinition workflow, CollectionBehaviour cfg,
      String stepId, CollectionAction action, String actorId) {
    if (StringUtils.isBlank(actorId)) {
      denyAndThrow(state, stepId, action, actorId, "actor must not be blank");
    }
    if (cfg.authorizationMode() == AuthorizationMode.OPEN) {
      if (action == CollectionAction.REPLACE_ANY || action == CollectionAction.WITHDRAW_ANY) {
        denyAndThrow(state, stepId, action, actorId,
            "OPEN mode permits only owner mutation; action '%s' denied".formatted(action.wire()));
      }
      if (action == CollectionAction.DEADLINE_CLOSE) {
        denyAndThrow(state, stepId, action, actorId,
            ("OPEN mode does not permit a deadline-triggered close; switch to ENFORCED and declare a "
                + "STEP_ACTION requirement for '%s' to authorize a specific actor")
                .formatted(action.wire()));
      }
      if (action == CollectionAction.OVERRIDE) {
        denyAndThrow(state, stepId, action, actorId,
            ("OPEN mode does not permit a minimum-override close; switch to ENFORCED and declare a "
                + "STEP_ACTION requirement for '%s' to authorize a specific actor")
                .formatted(action.wire()));
      }
      return;
    }
    WorkflowRequirement requirement = findStepActionRequirement(workflow, stepId, action);
    if (requirement == null) {
      denyAndThrow(state, stepId, action, actorId,
          "no STEP_ACTION requirement declared for action '%s'".formatted(action.wire()));
    }
    ResolutionContext context = new ResolutionContext(state.getWorkflowId(), state.getRunId(), Map.of());
    Decision decision;
    try {
      decision = collectionAuthorizer.authorize(actorId, stepId, action,
          requirementResolver.resolve(requirement, context), context);
    } catch (RuntimeException ex) {
      LOG.log(System.Logger.Level.WARNING, "CollectionAuthorizer threw; failing closed", ex);
      denyAndThrow(state, stepId, action, actorId, "authorizer error");
      return;
    }
    if (decision == null || !decision.allowed()) {
      denyAndThrow(state, stepId, action, actorId,
          decision == null ? "authorizer returned no decision" : decision.reason());
    }
  }

  private void denyAndThrow(WorkflowState state, String stepId, CollectionAction action,
      String actorId, String reason) {
    String auditActor = StringUtils.defaultIfBlank(actorId, "runtime");
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("actorId", auditActor);
    payload.put("action", action.wire());
    payload.put("reason", reason);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED,
        toJson(payload), auditActor);
    throw new CollectionAuthorizationException(actorId, stepId, action, reason);
  }

  private static CollectionAction resolveReplaceAction(ReplacementPolicy policy, boolean owns, String stepId) {
    Validate.isTrue(policy != ReplacementPolicy.NONE,
        "Replacement is not permitted for collection step '%s'".formatted(stepId));
    if (policy == ReplacementPolicy.OWNER_REPLACE) {
      Validate.isTrue(owns, "Replacement is restricted to the submitting actor");
      return CollectionAction.REPLACE_OWN;
    }
    return owns ? CollectionAction.REPLACE_OWN : CollectionAction.REPLACE_ANY;
  }

  private static CollectionAction resolveWithdrawAction(WithdrawalPolicy policy, boolean owns, String stepId) {
    Validate.isTrue(policy != WithdrawalPolicy.NONE,
        "Withdrawal is not permitted for collection step '%s'".formatted(stepId));
    if (policy == WithdrawalPolicy.OWNER_WITHDRAW) {
      Validate.isTrue(owns, "Withdrawal is restricted to the submitting actor");
      return CollectionAction.WITHDRAW_OWN;
    }
    return owns ? CollectionAction.WITHDRAW_OWN : CollectionAction.WITHDRAW_ANY;
  }

  private static WorkflowRequirement findStepActionRequirement(WorkflowDefinition workflow,
      String stepId, CollectionAction action) {
    for (WorkflowRequirement requirement : workflow.requirements()) {
      if (requirement.scope() == RequirementScope.STEP_ACTION
          && stepId.equals(requirement.stepId())
          && action.wire().equals(requirement.action())) {
        return requirement;
      }
    }
    return null;
  }

  private String checkSubmitConstraints(CollectionState gate, CollectionBehaviour cfg,
      CollectionSubmission submission, String actorId) {
    List<CollectionItem> live = liveItems(gate);
    if (cfg.maxItems() != null && live.size() >= cfg.maxItems()) {
      return "MAX_ITEMS";
    }
    if (cfg.maxItemsPerActor() != null
        && live.stream().filter(item -> item.submittedByActorId().equals(actorId)).count()
        >= cfg.maxItemsPerActor()) {
      return "PER_ACTOR_CAP";
    }
    String oversize = checkOversize(cfg, submission.payload());
    if (oversize != null) {
      return oversize;
    }
    if (dedupeCollides(live, cfg.duplicatePolicy(), submission.dedupeKey(), null)) {
      return "DUPLICATE";
    }
    return null;
  }

  private String checkReplaceConstraints(CollectionState gate, CollectionBehaviour cfg,
      String submissionId, CollectionSubmission replacement) {
    String oversize = checkOversize(cfg, replacement.payload());
    if (oversize != null) {
      return oversize;
    }
    if (dedupeCollides(liveItems(gate), cfg.duplicatePolicy(), replacement.dedupeKey(), submissionId)) {
      return "DUPLICATE";
    }
    return null;
  }

  /** Whether the client token was already seen at this gate (any prior submit or replace). */
  private static boolean clientTokenSeen(CollectionState gate, String clientToken) {
    return clientToken != null && gate.seenClientTokens().contains(clientToken);
  }

  /**
   * Validates the item's inline JSON against the gate's declared {@code itemSchemaRef}, if any.
   * Fail-closed on every path: a declared reference with no inline JSON, an unresolvable
   * reference, or a schema violation all reject the item.
   *
   * @return a rejection reason, or {@code null} when no schema is declared or the item conforms
   */
  private String checkItemSchema(CollectionBehaviour cfg, CollectionPayload payload) {
    if (cfg.itemSchemaRef() == null) {
      return null;
    }
    if (payload.inlineJson() == null || payload.inlineJson().isBlank()) {
      return "ITEM_SCHEMA_INVALID: the gate declares itemSchemaRef '%s' but the item carries no inline JSON"
          .formatted(cfg.itemSchemaRef());
    }
    ValidationResult result;
    try {
      result = itemSchemaValidator.validate(cfg.itemSchemaRef(), payload.inlineJson());
    } catch (RuntimeException ex) {
      LOG.log(System.Logger.Level.WARNING, "CollectionItemSchemaValidator threw; failing closed", ex);
      return "ITEM_SCHEMA_INVALID: item-schema validator error";
    }
    if (result == null) {
      return "ITEM_SCHEMA_INVALID: the item-schema validator returned no result";
    }
    if (!result.valid()) {
      return "ITEM_SCHEMA_INVALID: %s".formatted(result.message());
    }
    return null;
  }

  /**
   * Consults the embedding application's {@link CollectionSubmissionValidator} after all declared
   * gate constraints passed. A {@code null} decision is treated as a denial (fail closed).
   *
   * @return a rejection reason, or {@code null} when the submission is admitted
   */
  private String checkSubmissionValidator(WorkflowState state, WorkflowDefinition workflow,
      CollectionBehaviour cfg, String stepId, CollectionState gate, CollectionSubmission submission,
      String actorId, String replacesSubmissionId) {
    Decision decision;
    try {
      decision = submissionValidator.validate(new CollectionSubmissionContext(
          state.getRunId(), workflow.id(), stepId, actorId, submission.payload(),
          submission.clientToken(), submission.dedupeKey(), replacesSubmissionId, cfg, gate));
    } catch (RuntimeException ex) {
      LOG.log(System.Logger.Level.WARNING, "CollectionSubmissionValidator threw; failing closed", ex);
      return "SUBMISSION_VALIDATOR_REJECTED: submission validator error";
    }
    if (decision == null) {
      return "SUBMISSION_VALIDATOR_REJECTED: the submission validator returned no decision";
    }
    if (!decision.allowed()) {
      return "SUBMISSION_VALIDATOR_REJECTED: %s".formatted(decision.reason());
    }
    return null;
  }

  /**
   * Whether {@code dedupeKey} collides with a live item's dedupe key under {@code REJECT_BY_DEDUPE_KEY}, excluding
   * {@code excludeSubmissionId} (the slot being replaced, if any) so a replacement is not rejected against its own
   * prior dedupe key.
   */
  private static boolean dedupeCollides(List<CollectionItem> live, DuplicatePolicy policy,
      String dedupeKey, String excludeSubmissionId) {
    if (policy != DuplicatePolicy.REJECT_BY_DEDUPE_KEY || dedupeKey == null) {
      return false;
    }
    return live.stream()
        .filter(item -> !item.submissionId().equals(excludeSubmissionId))
        .anyMatch(item -> dedupeKey.equals(item.dedupeKey()));
  }

  private static String checkOversize(CollectionBehaviour cfg, CollectionPayload payload) {
    if (payload.inlineJson() != null
        && payload.inlineJson().getBytes(StandardCharsets.UTF_8).length
        > cfg.maxInlinePayloadBytes()) {
      return "OVERSIZE";
    }
    return null;
  }

  private static String checkClosable(CollectionBehaviour cfg, CloseReason reason) {
    if (reason == CloseReason.MANUAL && !cfg.manualClose()) {
      return "manual close is not permitted";
    }
    if (reason == CloseReason.DEADLINE && !cfg.externalDeadlineClosable()) {
      return "deadline close is not permitted";
    }
    return null;
  }

  private static SubmissionResult idempotentReturn(CollectionState gate, String clientToken) {
    if (clientToken == null || !gate.seenClientTokens().contains(clientToken)) {
      return null;
    }
    for (CollectionItem item : gate.items()) {
      if (clientToken.equals(item.clientToken())) {
        return new SubmissionResult(SubmissionResult.Status.IDEMPOTENT, item.submissionId(),
            item.version(), null);
      }
    }
    return new SubmissionResult(SubmissionResult.Status.IDEMPOTENT, null, 0, null);
  }

  private void emitRejected(WorkflowState state, String stepId, String actorId,
      CollectionAction action, String reason) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("actorId", actorId);
    payload.put("action", action.wire());
    payload.put("reason", reason);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_ITEM_REJECTED,
        toJson(payload), actorId);
  }

  private void emitCloseRejected(WorkflowState state, String stepId, String actorId, String reason,
      int itemCount, int minItems) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("actorId", actorId);
    payload.put("reason", reason);
    payload.put("itemCount", itemCount);
    payload.put("minItems", minItems);
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_CLOSE_REJECTED,
        toJson(payload), actorId);
  }

  private ObjectNode submittedPayload(String submissionId, String actorId, CollectionItem item) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("submissionId", submissionId);
    payload.put("actorId", actorId);
    payload.put("fileCount", item.payload().files().size());
    payload.put("hasInline", item.payload().inlineJson() != null);
    return payload;
  }

  private static CollectionState requireGate(WorkflowState state, String stepId) {
    return state.getCollectionState(stepId).orElseThrow(() -> new IllegalArgumentException(
        "No collection gate state for step '%s'".formatted(stepId)));
  }

  private static CollectionState requireOpenGate(WorkflowState state, String stepId) {
    CollectionState gate = requireGate(state, stepId);
    Validate.isTrue(gate.phase() == CollectionPhase.OPEN,
        "Collection step '%s' is not open".formatted(stepId));
    return gate;
  }

  private static CollectionItem requireLiveItem(CollectionState gate, String submissionId) {
    for (CollectionItem item : gate.items()) {
      if (item.submissionId().equals(submissionId) && !item.withdrawn()) {
        return item;
      }
    }
    throw new IllegalArgumentException("No live item '%s' in collection".formatted(submissionId));
  }

  private static List<CollectionItem> liveItems(CollectionState gate) {
    List<CollectionItem> live = new ArrayList<>();
    for (CollectionItem item : gate.items()) {
      if (!item.withdrawn()) {
        live.add(item);
      }
    }
    return List.copyOf(live);
  }

  private static List<CollectionItem> replaceSlot(List<CollectionItem> items, String submissionId,
      CollectionItem replacement) {
    List<CollectionItem> next = new ArrayList<>(items.size());
    for (CollectionItem item : items) {
      next.add(item.submissionId().equals(submissionId) ? replacement : item);
    }
    return next;
  }

  private void storeGate(WorkflowState state, CollectionState gate) {
    state.putCollectionState(gate);
    state.setLastUpdatedAt(clock.instant());
  }

  private static CollectionState withItemsAndSeenToken(CollectionState gate,
      List<CollectionItem> items, String clientToken) {
    Set<String> seen = gate.seenClientTokens();
    if (clientToken != null && !seen.contains(clientToken)) {
      Set<String> next = new LinkedHashSet<>(seen);
      next.add(clientToken);
      seen = next;
    }
    return new CollectionState(gate.stepId(), gate.phase(), gate.openedAt(), items, gate.closedAt(),
        gate.closedByActorId(), gate.closeReason(), seen, gate.seenCloseTokens(), gate.version() + 1);
  }

  private CollectionState closedGate(CollectionState gate, CloseRequest request, CloseReason reason) {
    Set<String> seenClose = gate.seenCloseTokens();
    if (request.closeToken() != null && !seenClose.contains(request.closeToken())) {
      Set<String> next = new LinkedHashSet<>(seenClose);
      next.add(request.closeToken());
      seenClose = next;
    }
    return new CollectionState(gate.stepId(), CollectionPhase.CLOSED, gate.openedAt(), gate.items(),
        clock.instant(), request.actorId(), reason, gate.seenClientTokens(), seenClose,
        gate.version() + 1);
  }

  private static CollectionState reopenedGate(CollectionState gate) {
    // seenCloseTokens is dropped, not carried over: a reopen starts a fresh close cycle, so a
    // replayed close call bearing a token from the prior (now-superseded) closed cycle must be
    // authorized and evaluated as a real close attempt against the reopened gate, not short-circuited
    // as an idempotent no-op that leaves the gate open while reporting success.
    return new CollectionState(gate.stepId(), CollectionPhase.OPEN, gate.openedAt(), gate.items(),
        null, null, null, gate.seenClientTokens(), Set.of(), gate.version() + 1);
  }
}
