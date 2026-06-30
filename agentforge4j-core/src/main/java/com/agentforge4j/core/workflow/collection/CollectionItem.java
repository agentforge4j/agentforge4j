// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * One logical submission slot in a collection gate. A replacement creates a new version of the same
 * {@code submissionId}; a withdrawal marks the slot withdrawn. The current materialized view keeps the
 * latest non-withdrawn version per {@code submissionId}.
 *
 * @param submissionId        non-blank runtime-assigned slot id (never client-chosen)
 * @param submittedByActorId  non-blank opaque actor that submitted this version
 * @param submittedAt         non-null submission timestamp
 * @param version             1-based version of this slot; increases on replacement
 * @param withdrawn           whether this slot has been withdrawn
 * @param payload             non-null content of this version
 * @param dedupeKey           optional caller-supplied opaque dedupe key; {@code null} when absent
 * @param clientToken         optional caller-supplied idempotency token; {@code null} when absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionItem(
    String submissionId,
    String submittedByActorId,
    Instant submittedAt,
    int version,
    boolean withdrawn,
    CollectionPayload payload,
    String dedupeKey,
    String clientToken
) {

  public CollectionItem {
    Validate.notBlank(submissionId, "CollectionItem submissionId must not be blank");
    Validate.notBlank(submittedByActorId, "CollectionItem submittedByActorId must not be blank");
    Validate.notNull(submittedAt, "CollectionItem submittedAt must not be null");
    Validate.isGreaterThanZero(version, "CollectionItem version must be at least 1");
    Validate.notNull(payload, "CollectionItem payload must not be null");
  }
}
