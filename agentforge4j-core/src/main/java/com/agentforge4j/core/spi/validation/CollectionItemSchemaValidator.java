// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.validation;

/**
 * SPI validating a collection-gate item's inline JSON against the JSON schema a
 * {@code COLLECTION} step declares via {@code itemSchemaRef}. The runtime consults this SPI on
 * every submit and replace whose gate declares an {@code itemSchemaRef}; a
 * {@link ValidationResult#invalid(String) invalid} (or {@code null}) result rejects the submission
 * with a {@code COLLECTION_ITEM_REJECTED} audit event. Gates without an {@code itemSchemaRef}
 * never reach this SPI.
 *
 * <p>Implementations must be deterministic, side-effect free, and safe for concurrent use. An
 * unknown {@code itemSchemaRef} must yield an invalid result (fail closed), never a pass: a
 * declared item contract that cannot be verified must not admit items.
 *
 * <p>The default used when no implementation is configured is {@link #unconfigured()}, which
 * rejects every declared reference so a declared contract is never silently unenforced.
 */
public interface CollectionItemSchemaValidator {

  /**
   * Validates one item's inline JSON against the referenced schema.
   *
   * @param itemSchemaRef the schema reference the collection step declares; never blank
   * @param inlineJson    the submitted item's inline JSON document; never blank
   *
   * @return the validation outcome
   */
  ValidationResult validate(String itemSchemaRef, String inlineJson);

  /**
   * Returns the fail-closed placeholder used when no validator is configured: every declared
   * {@code itemSchemaRef} is rejected, so a workflow's declared item contract can never pass
   * unverified. Configure a real validator (or a schemas directory on the bootstrap) to run
   * workflows that declare item schemas.
   *
   * @return a validator rejecting every reference
   */
  static CollectionItemSchemaValidator unconfigured() {
    return (itemSchemaRef, inlineJson) -> ValidationResult.invalid(
        "no CollectionItemSchemaValidator is configured; cannot verify declared itemSchemaRef '%s'"
            .formatted(itemSchemaRef));
  }
}
