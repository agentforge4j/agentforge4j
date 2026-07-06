// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

/**
 * {@link CompactionMode} that compacts a schema-typed source (a ledger or schema-typed artifact) by
 * dropping rationale fields, resolved questions, and superseded entries. Lossless for traceability
 * because entry ids are retained as references. Never invokes an LLM.
 */
public record DeterministicExtract() implements CompactionMode {

}
