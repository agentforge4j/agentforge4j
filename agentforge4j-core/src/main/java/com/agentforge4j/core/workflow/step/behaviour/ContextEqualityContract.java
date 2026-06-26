// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;

/**
 * A deterministic equality contract a {@code VALIDATE} step enforces between a generated artifact and a run-context
 * value: the JSON value at {@code jsonPointer} within the artifact at {@code artifactPath} must equal the value of
 * context key {@code contextKey} (exposed to the step via its {@code inputKeys}). The runtime evaluates the contract
 * and fails the run closed on a missing context value, a missing/typed-mismatched pointer target, or inequality.
 *
 * @param artifactPath non-blank captured-artifact path the pointer is resolved within
 * @param jsonPointer  non-blank RFC 6901 JSON Pointer (for example {@code /modelTier})
 * @param contextKey   non-blank context key whose value the pointer target must equal
 */
public record ContextEqualityContract(String artifactPath, String jsonPointer, String contextKey) {

  public ContextEqualityContract {
    Validate.notBlank(artifactPath, "ContextEqualityContract artifactPath must not be blank");
    Validate.notBlank(jsonPointer, "ContextEqualityContract jsonPointer must not be blank");
    Validate.notBlank(contextKey, "ContextEqualityContract contextKey must not be blank");
  }
}
