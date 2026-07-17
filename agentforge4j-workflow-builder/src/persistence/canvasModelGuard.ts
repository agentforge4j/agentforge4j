// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel } from '../model/canvasModel';

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Minimal structural gate a value must pass before it may replace the live canvas model.
 *
 * A draft that fails this check — a stale shape persisted by an older builder version, a
 * truncated write, or a host adapter resolving something unexpected — is discarded instead
 * of restored. Restoring it would crash the editor at render time (the render path iterates
 * `nodes`/`edges` unconditionally), and because the bad draft would still be stored, every
 * remount would crash again with no UI-level way out: the "Start fresh" action lives inside
 * the very tree that crashed.
 *
 * Deliberately checks structure only (field presence and container types), not deep node
 * semantics — unknown extra fields must stay restorable so the check does not reject drafts
 * from a *newer* builder version that only added fields.
 */
export function isRestorableCanvasModel(value: unknown): value is CanvasModel {
  if (!isPlainObject(value)) {
    return false;
  }
  if (typeof value.workflowId !== 'string' || typeof value.workflowName !== 'string' || typeof value.description !== 'string') {
    return false;
  }
  if (value.startNodeId !== null && typeof value.startNodeId !== 'string') {
    return false;
  }
  if (!Array.isArray(value.nodes) || !Array.isArray(value.edges)) {
    return false;
  }
  const nodesValid = value.nodes.every(
    (node) =>
      isPlainObject(node) &&
      typeof node.id === 'string' &&
      typeof node.kind === 'string' &&
      isPlainObject(node.position) &&
      isPlainObject(node.data),
  );
  if (!nodesValid) {
    return false;
  }
  const edgesValid = value.edges.every(
    (edge) => isPlainObject(edge) && typeof edge.id === 'string' && typeof edge.source === 'string' && typeof edge.target === 'string',
  );
  if (!edgesValid) {
    return false;
  }
  return isPlainObject(value.artifacts) && isPlainObject(value.blueprints);
}
