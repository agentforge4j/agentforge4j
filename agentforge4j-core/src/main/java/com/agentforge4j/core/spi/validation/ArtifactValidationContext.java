// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.validation;

import java.util.Map;

/**
 * Read-only view passed to an {@link ArtifactValidator}: the artifacts the selecting {@code VALIDATE} step declared in
 * its {@code requiredArtifacts}, captured in-process for the run and keyed by their requested path. The view is scoped
 * to that step's declared paths — artifacts captured for other {@code VALIDATE} steps in the same run are never visible
 * here — so a validator sees exactly the bundle it was selected to check. The bytes are the authoritative emitted
 * content (never read from disk or an LLM-echoed copy). Generic checks (required-file allowlist, path safety, context
 * equality) are applied by the runtime before the validator runs; the validator performs only its format-specific
 * parsing and semantic validation.
 */
@FunctionalInterface
public interface ArtifactValidationContext {

  /**
   * Returns the selecting step's declared artifacts as an immutable {@code path -> content} map.
   *
   * @return immutable map of artifact path to emitted content
   */
  Map<String, String> artifacts();
}
