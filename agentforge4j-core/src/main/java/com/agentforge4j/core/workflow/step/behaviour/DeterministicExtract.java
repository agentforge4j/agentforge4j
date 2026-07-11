// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

/**
 * {@link CompactionMode} that compacts a whole-ledger source by copying the envelope verbatim
 * except stripping the top-level {@code rationale} field from each entry. Entry ids,
 * {@code openQuestions}, and {@code conflicts} are always carried forward — structural fields are
 * exempt from compaction, so the compact form stays traceable back to the source. Never invokes an
 * LLM. Only implemented for {@code LEDGER_SECTION} sources naming a whole ledger (enforced at load
 * time and again by the runtime handler).
 */
public record DeterministicExtract() implements CompactionMode {

}
