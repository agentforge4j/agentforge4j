// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

/**
 * How strictly an agent's response is expected to conform to its {@link OutputContract} schema.
 *
 * <p>What the runtime currently does with this declaration: {@code discipline} and
 * {@code schemaRef} are validated at load time ({@link OutputContract}'s canonical constructor
 * requires a {@code schemaRef} when {@code discipline} is {@link #STRUCTURED_ONLY}). The runtime
 * does not yet validate a response against the contract schema at invocation, for any discipline
 * value — response conformance is not currently enforced or checked.
 */
public enum OutputDiscipline {

  /**
   * The response must be the JSON object conforming to the contract schema. Not yet enforced at
   * invocation (see the class-level note); a step's existing bounded retry/fail semantics are
   * unaffected by contract non-conformance today.
   */
  STRUCTURED_ONLY,

  /**
   * A conforming JSON object is preferred but non-conforming output is tolerated. Not yet validated
   * at invocation (see the class-level note).
   */
  STRUCTURED_PREFERRED,

  /**
   * No structural expectation; the response is free-form text.
   */
  FREEFORM
}
