// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';

/**
 * Replaces the editor entirely when the builder's own rendered container is narrower
 * than the supported breakpoint (see `useNarrowContainerGate`). Per the 0.1.0 scope
 * decision there is no reduced or read-only mobile editing surface — this message is
 * the whole of the narrow-container experience; the canvas, palette, inspector, and
 * toolbar are not mounted underneath it.
 */
export function NarrowContainerNotice() {
  return (
    <div className="workflow-builder__narrow-notice" role="status" data-testid="workflow-builder-narrow-notice">
      <p className="workflow-builder__narrow-notice-title">{ACTION_LABELS.narrowContainerTitle}</p>
      <p className="workflow-builder__narrow-notice-body">{ACTION_LABELS.narrowContainerBody}</p>
    </div>
  );
}
