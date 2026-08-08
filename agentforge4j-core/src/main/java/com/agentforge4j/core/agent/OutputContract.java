// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

import com.agentforge4j.util.Validate;

/**
 * Optional declaration on an {@link AgentDefinition} of the structured output an agent is expected to
 * produce: the shortest usable valid response rather than prose around JSON.
 *
 * <p>{@code schemaRef} is an <strong>opaque string</strong> in {@code core}: {@code core} depends on
 * no JSON-schema library and never resolves or validates the reference here. Reference resolution is a
 * config-load concern; invocation-time output validation is a runtime concern.
 *
 * @param schemaRef        classpath- or bundle-relative id of the JSON schema the response conforms
 *                         to; must be non-blank when {@code discipline} is
 *                         {@link OutputDiscipline#STRUCTURED_ONLY}, otherwise {@code null} or
 *                         non-blank (a present reference is never blank)
 * @param discipline       how strictly the response must conform; never {@code null}
 * @param rationaleAllowed whether the contract schema may carry a bounded {@code rationale} field so
 *                         reasoning-heavy agents keep explanation inside the contract, not around it
 */
public record OutputContract(
    String schemaRef,
    OutputDiscipline discipline,
    boolean rationaleAllowed
) {

  public OutputContract {
    Validate.notNull(discipline, "OutputContract discipline must not be null");
    if (discipline == OutputDiscipline.STRUCTURED_ONLY) {
      Validate.notBlank(schemaRef,
          "OutputContract schemaRef must not be blank when discipline is STRUCTURED_ONLY");
    } else if (schemaRef != null) {
      Validate.notBlank(schemaRef,
          "OutputContract schemaRef must be null or non-blank");
    }
  }
}
