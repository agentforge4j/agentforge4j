// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * A minimal, typed epic breakdown for Mode 2 (SDLC / epic-package) estimation — the caller-supplied
 * package of epics an Epic Creator (or an equivalent requirements breakdown) produced. Deliberately
 * small: only what deterministic epic/phase/loop sizing needs, per the closed design (a small typed
 * model, not a loose summary string, and not the Full Application SDLC workflow's own definition,
 * which does not exist yet).
 *
 * @param packageId non-blank identity for the package (used as the {@code workflowId} field on the
 *                  resulting analysis)
 * @param epics     non-empty ordered list of epics
 */
public record EpicPackage(String packageId, List<Epic> epics) {

  public EpicPackage {
    Validate.notBlank(packageId, "EpicPackage packageId must not be blank");
    Validate.notEmpty(epics, "EpicPackage epics must not be empty");
    epics = List.copyOf(epics);
  }
}
