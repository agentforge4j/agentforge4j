// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;

/**
 * One epic within an {@link EpicPackage} — the minimal shape Mode 2 estimation needs: identity and
 * an optional expected-case rework-iteration hint. Deliberately lean: no requirements text, no
 * acceptance criteria, no ownership metadata — those belong to the Epic Creator's own output format,
 * not to sizing.
 *
 * @param epicId                    non-blank unique id within the package
 * @param name                      non-blank display name
 * @param expectedReworkIterations  optional expected-case rework-loop iteration hint (the
 *                                  expected-case companion to the analyzer's assumed per-phase
 *                                  rework ceiling); {@code null} when no hint is supplied. When
 *                                  present it must be at least one. Advisory only
 */
public record Epic(String epicId, String name, Integer expectedReworkIterations) {

  public Epic {
    Validate.notBlank(epicId, "Epic epicId must not be blank");
    Validate.notBlank(name, "Epic name must not be blank for epic: %s".formatted(epicId));
    if (expectedReworkIterations != null) {
      Validate.isGreaterThanZero(expectedReworkIterations,
          "Epic expectedReworkIterations must be at least 1 when supplied for epic: %s"
              .formatted(epicId));
    }
  }
}
