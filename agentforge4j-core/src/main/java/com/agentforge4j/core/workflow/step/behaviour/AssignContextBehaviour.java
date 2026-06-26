// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import com.agentforge4j.util.Validate;

/**
 * Deterministically assigns a literal scalar {@link ContextValue} to {@code contextKey}, with no LLM call. Used to
 * write a fixed control value (for example a resolved recommended tier) into the run context. The runtime re-stamps the
 * value's provenance as system-generated on write.
 *
 * <p>Constraints: only scalar values (STRING / NUMBER / BOOLEAN) are permitted; the reserved
 * {@code __} runtime namespace and the untrusted-input envelope key are rejected, so an assignment can never overwrite
 * a protected key.
 *
 * @param contextKey non-blank, non-reserved key to write
 * @param value      non-null scalar value to assign
 */
public record AssignContextBehaviour(String contextKey, ContextValue value) implements StepBehaviour {

  public AssignContextBehaviour {
    Validate.notBlank(contextKey, "AssignContextBehaviour contextKey must not be blank");
    Validate.isTrue(!contextKey.startsWith("__"),
        "AssignContextBehaviour must not target a reserved '__' context key: %s".formatted(contextKey));
    Validate.isTrue(!UntrustedInputEnvelope.KEY.equals(contextKey),
        "AssignContextBehaviour must not target the reserved untrusted-input key: %s"
            .formatted(contextKey));
    Validate.notNull(value, "AssignContextBehaviour value must not be null");
    Validate.isTrue(
        value instanceof StringContextValue || value instanceof NumberContextValue
            || value instanceof BooleanContextValue,
        "AssignContextBehaviour value must be a scalar (STRING, NUMBER, or BOOLEAN)");
  }
}
