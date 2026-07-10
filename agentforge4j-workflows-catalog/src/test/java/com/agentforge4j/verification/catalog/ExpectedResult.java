// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * The {@code expected-result.json} bundle for one catalog scenario: the workflow to run, the ordered
 * human gate responses that drive it, and the assertions to project onto the captured run.
 *
 * @param workflowId id of the shipped workflow to drive
 * @param gates      ordered human responses drained one per pause; may be {@code null} (none)
 * @param expect     assertions to apply to the run result
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpectedResult(String workflowId, List<GateSpec> gates, ExpectSpec expect) {

  /**
   * One scripted human response. {@code type} is one of {@code input}, {@code review},
   * {@code stepApproval}, {@code escalation}, {@code toolApprove}, {@code toolReject},
   * {@code toolContinue}, {@code toolRetry}.
   *
   * @param type             the gate kind
   * @param answers          artifact item-id → answer map (for {@code input}); may be {@code null}
   * @param approve          approve/reject flag (for {@code stepApproval}); may be {@code null}
   * @param note             note recorded on the event (for {@code review} / {@code stepApproval} /
   *                         {@code escalation}); may be {@code null}
   * @param reason           rejection reason recorded for a {@code toolReject} gate; may be
   *                         {@code null}
   * @param toolInvocationId explicit pending tool-invocation id for a tool gate; {@code null}
   *                         auto-targets the run's single current pending invocation
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GateSpec(String type, Map<String, String> answers, Boolean approve, String note,
      String reason, String toolInvocationId, List<CollectionOpSpec> ops) {

    /**
     * One operation in a {@code collection} gate's ordered op list.
     *
     * @param op           {@code submit} / {@code replace} / {@code withdraw} / {@code close}
     * @param payloadRef   inline JSON payload for submit/replace; may be {@code null}
     * @param submissionId 0-based ordinal of the originating submit for replace/withdraw; may be
     *                     {@code null}
     * @param clientToken  optional idempotency token for submit; may be {@code null}
     * @param dedupeKey    optional dedupe key for submit; may be {@code null}
     * @param reason       close reason for a close op; may be {@code null}
     * @param override     close-despite-unmet-minimum flag for a close op; may be {@code null}
     * @param actorId      the acting actor for submit/replace/withdraw; {@code null} defaults to the
     *                     harness's actor, so a scenario can script a multi-submitter collection or
     *                     assert an owner-scoped denial
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CollectionOpSpec(String op, String payloadRef, Integer submissionId,
        String clientToken, String dedupeKey, String reason, Boolean override, String actorId) {
    }
  }

  /**
   * Assertions to apply. Every field is optional; a {@code null} field is not asserted.
   *
   * @param status         expected terminal/pending {@code WorkflowStatus} name
   * @param context        expected context key → string value entries
   * @param visitedSteps   step ids that must have been visited
   * @param notVisitedSteps step ids that must not have been visited
   * @param emittedEvents  {@code WorkflowEventType} names that must have been emitted
   * @param createdFiles   file paths that must have been created
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExpectSpec(String status, Map<String, String> context, List<String> visitedSteps,
      List<String> notVisitedSteps, List<String> emittedEvents, List<String> createdFiles) {
  }
}
