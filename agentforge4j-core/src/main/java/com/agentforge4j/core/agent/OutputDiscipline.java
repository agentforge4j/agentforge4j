// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

/**
 * How strictly an agent's response is expected to conform to its {@link OutputContract} schema.
 */
public enum OutputDiscipline {

  /**
   * The response must be the JSON object conforming to the contract schema; invalid output flows into
   * the step's existing bounded retry/fail semantics.
   */
  STRUCTURED_ONLY,

  /**
   * A conforming JSON object is preferred but non-conforming output is tolerated (validated when a
   * schema is present, not enforced as a failure).
   */
  STRUCTURED_PREFERRED,

  /**
   * No structural expectation; the response is free-form text.
   */
  FREEFORM
}
