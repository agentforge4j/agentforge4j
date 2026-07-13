// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../copy/workflow-terminology';
import { startStepCandidateIds } from '../model/graphOps';
import { NODE_KIND_META } from '../model/nodeKinds';
import type { CanvasModel } from '../model/canvasModel';

export type StartStepChooserProps = {
  model: CanvasModel;
  onSelectStart: (nodeId: string) => void;
};

function labelForNode(model: CanvasModel, id: string): string {
  const found = model.nodes.find((n) => n.id === id);
  return found ? found.data.name?.trim() || NODE_KIND_META[found.kind].label : id;
}

/**
 * A direct, Guided-mode-visible chooser for the workflow's start step.
 *
 * Renders only once the workflow has more than one node — with a single node
 * there is nothing to choose between (the canvas's starter hint already covers
 * that case in Advanced mode; Guided mode's own first stepper stage covers it
 * here). Reassigns `startNodeId` via the same {@link repositionAfter}/
 * `START_SENTINEL` mechanism the inspector's "Runs after: Start" option uses —
 * this is a more discoverable entry point onto that existing mutation path, not
 * a parallel one.
 */
export function StartStepChooser({ model, onSelectStart }: StartStepChooserProps) {
  if (model.nodes.length < 2) {
    return null;
  }
  const currentId = model.startNodeId;
  const candidateIds = startStepCandidateIds(model);
  if (candidateIds.length === 0) {
    return null;
  }

  return (
    <section className="wf-guided-start wf-panel" aria-label={ACTION_LABELS.startStepField}>
      <label className="wf-field">
        <span className="wf-field__label">{ACTION_LABELS.startStepField}</span>
        <select
          className="wf-input wf-select"
          data-testid="guided-start-step-select"
          value={currentId ?? ''}
          onChange={(e) => {
            const next = e.target.value;
            if (next && next !== currentId) {
              onSelectStart(next);
            }
          }}
        >
          {currentId ? <option value={currentId}>{labelForNode(model, currentId)}</option> : null}
          {candidateIds.map((id) => (
            <option key={id} value={id}>
              {labelForNode(model, id)}
            </option>
          ))}
        </select>
      </label>
      <p className="wf-field__hint">{ACTION_LABELS.startStepHint}</p>
    </section>
  );
}
