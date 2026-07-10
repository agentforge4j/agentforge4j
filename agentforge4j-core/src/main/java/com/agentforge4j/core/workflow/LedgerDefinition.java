// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.util.Validate;

/**
 * Declaration of a structured, schema-typed, deterministically merged ledger section of workflow
 * state. Ledgers are the primary state-capture mechanism; LLM summarization is the exception.
 *
 * <p>{@code schemaRef} is an <strong>opaque string</strong> in {@code core}: reference resolution and
 * validation of the ledger schema (and of {@code mergeKeyField} against that schema) are config-load
 * concerns, not performed here; the shipped loader does not yet perform them either.
 *
 * @param id            non-blank ledger id, for example {@code "requirements"}
 * @param schemaRef     non-blank id of the JSON schema each ledger entry conforms to
 * @param mergeStrategy deterministic merge strategy; never {@code null}
 * @param mergeKeyField the entry field merged on; must be non-blank when {@code mergeStrategy} is
 *                      {@link LedgerMergeStrategy#MERGE_BY_KEY} and must be {@code null} otherwise
 */
public record LedgerDefinition(
    String id,
    String schemaRef,
    LedgerMergeStrategy mergeStrategy,
    String mergeKeyField
) {

  public LedgerDefinition {
    Validate.notBlank(id, "LedgerDefinition id must not be blank");
    Validate.notBlank(schemaRef,
        "LedgerDefinition schemaRef must not be blank for ledger: %s".formatted(id));
    Validate.notNull(mergeStrategy,
        "LedgerDefinition mergeStrategy must not be null for ledger: %s".formatted(id));
    if (mergeStrategy == LedgerMergeStrategy.MERGE_BY_KEY) {
      Validate.notBlank(mergeKeyField,
          "LedgerDefinition mergeKeyField must not be blank when mergeStrategy is MERGE_BY_KEY "
              + "for ledger: %s".formatted(id));
    } else {
      Validate.isTrue(mergeKeyField == null,
          "LedgerDefinition mergeKeyField must be null unless mergeStrategy is MERGE_BY_KEY "
              + "for ledger: %s".formatted(id));
    }
  }
}
