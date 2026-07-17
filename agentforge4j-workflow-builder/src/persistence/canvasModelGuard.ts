// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel } from '../model/canvasModel';
import { NODE_KIND_META } from '../model/nodeKinds';

const KNOWN_NODE_KINDS = new Set(Object.keys(NODE_KIND_META));

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Whether `node.data` carries the containers its renderer/mapper dereference
 * unconditionally for the given `kind` — beyond this, per-kind field contents are not
 * validated (see the class doc below). `ASK_USER.artifactItems` is iterated directly by
 * `StepConfigPanel` (inspector) and `DECISION.cases` by both `DecisionNode` (canvas) and
 * `canvasNodeToStep` (invoked on every restore via the model→draft sync effect) — neither
 * call site optional-chains, so a missing array there crashes render/mapping outright
 * rather than merely rendering a placeholder.
 */
function hasRequiredKindContainers(kind: string, data: Record<string, unknown>): boolean {
  switch (kind) {
    case 'ASK_USER':
      return Array.isArray(data.artifactItems);
    case 'DECISION':
      return Array.isArray(data.cases);
    default:
      return true;
  }
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
 * from a *newer* builder version that only added fields. `kind` is the one exception: it is
 * checked against the running bundle's known `NodeKind` set (not just `typeof === 'string'`),
 * because an unrecognized kind is unrenderable in the running bundle regardless of what future
 * versions might add — `NODE_KIND_META[kind]` has no fallback anywhere it's looked up.
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
      KNOWN_NODE_KINDS.has(node.kind) &&
      isPlainObject(node.position) &&
      isPlainObject(node.data) &&
      hasRequiredKindContainers(node.kind, node.data),
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
