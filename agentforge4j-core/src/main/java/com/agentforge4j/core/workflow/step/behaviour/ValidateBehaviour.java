// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Deterministic, in-workflow validation of the artifacts a prior step emitted via {@code CREATE_FILE} and captured in
 * the run-scoped generated-artifact store. The runtime applies generic rules — the {@code requiredArtifacts} allowlist
 * (exactly those paths captured, no unexpected files), relative path safety, and the {@code contextEqualityContracts} —
 * then delegates format-specific parsing and semantic validation to the {@code ArtifactValidator} named by
 * {@code validatorId}. Any failure fails the run closed; the behaviour never carries a human transition.
 *
 * @param validatorId              non-blank id of the {@code ArtifactValidator} to apply
 * @param requiredArtifacts        non-empty allowlist of artifact paths that must be exactly present
 * @param contextEqualityContracts artifact-to-context equality contracts; may be empty
 */
public record ValidateBehaviour(
    String validatorId,
    List<String> requiredArtifacts,
    List<ContextEqualityContract> contextEqualityContracts
) implements StepBehaviour {

  public ValidateBehaviour {
    Validate.notBlank(validatorId, "ValidateBehaviour validatorId must not be blank");
    requiredArtifacts = List.copyOf(
        Validate.notEmpty(requiredArtifacts, "ValidateBehaviour requiredArtifacts must not be empty"));
    contextEqualityContracts = contextEqualityContracts == null
        ? List.of()
        : List.copyOf(contextEqualityContracts);
  }
}
