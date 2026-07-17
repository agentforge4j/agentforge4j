// SPDX-License-Identifier: Apache-2.0

/**
 * Step-connection graph operations for the workflow builder (Part A).
 *
 * These helpers operate on the authoritative {@link CanvasModel} graph state.
 * They rewrite ONLY the ordinary linear continuation edges (edges whose
 * `sourceHandle` is null) and, for repositioning, the top-level `startNodeId`.
 * DECISION/RETRY routing and REPEAT body membership live in node data and are
 * never touched here. None of the prohibited mapper logic
 * (`canvasToWorkflow`/`topoMainNodeOrder`) is changed; the only shared mapper
 * helper consumed is `collectBranchNestedIds`, so the branch-owned exclusion
 * matches exactly what the serializer treats as branch-nested.
 */

import type { CanvasEdge, CanvasModel, CanvasNode, DecisionNodeData, RepeatNodeData, RetryNodeData } from './canvasModel';
import { collectBranchNestedIds } from './mapper';

/** Sentinel target meaning "make this the first step in the (top-level) scope". */
export const START_SENTINEL = '__start__';

/** An ordinary "runs next" edge — the only edge class reposition/split rewrite. */
export function isLinearEdge(edge: CanvasEdge): boolean {
  return edge.sourceHandle == null;
}

function isTopLevel(node: CanvasNode): boolean {
  return !node.parentNode;
}

function linearEdgesInto(model: CanvasModel, id: string): CanvasEdge[] {
  return model.edges.filter((e) => isLinearEdge(e) && e.target === id);
}

function linearEdgesOutOf(model: CanvasModel, id: string): CanvasEdge[] {
  return model.edges.filter((e) => isLinearEdge(e) && e.source === id);
}

function linkEdge(source: string, target: string, usedIds: Set<string>): CanvasEdge {
  const base = `e-${source}-${target}-link`;
  let id = base;
  let suffix = 1;
  while (usedIds.has(id)) {
    id = `${base}-${suffix}`;
    suffix += 1;
  }
  usedIds.add(id);
  return { id, source, target, sourceHandle: null, label: null };
}

/**
 * Reposition `nodeId` to run after `afterId` (or first, when
 * `afterId === START_SENTINEL`) by rewriting linear edges only. Detaches the
 * moved node (closing `pred → node → succ` into `pred → succ`) and inserts it
 * after the target. Node-data routing is untouched. Returns a new model;
 * callers pass it through `setModel((m) => repositionAfter(m, …))`.
 */
export function repositionAfter(model: CanvasModel, nodeId: string, afterId: string): CanvasModel {
  const predEdge = model.edges.find((e) => isLinearEdge(e) && e.target === nodeId) ?? null;
  const succEdge = model.edges.find((e) => isLinearEdge(e) && e.source === nodeId) ?? null;
  const predecessor = predEdge ? predEdge.source : null;
  const successor = succEdge ? succEdge.target : null;

  let edges = model.edges.filter((e) => e !== predEdge && e !== succEdge);
  const usedIds = new Set(edges.map((e) => e.id));
  let startNodeId = model.startNodeId;

  // Detach: close the gap the moved node leaves behind.
  if (predecessor && successor) {
    edges = [...edges, linkEdge(predecessor, successor, usedIds)];
  } else if (!predecessor && successor && model.startNodeId === nodeId) {
    // The moved node was the scope entry; its successor becomes the new entry.
    startNodeId = successor;
  }

  if (afterId === START_SENTINEL) {
    const oldFirst = startNodeId;
    startNodeId = nodeId;
    if (oldFirst && oldFirst !== nodeId) {
      edges = [...edges, linkEdge(nodeId, oldFirst, usedIds)];
    }
  } else {
    const afterSuccEdge = edges.find((e) => isLinearEdge(e) && e.source === afterId) ?? null;
    const afterSucc = afterSuccEdge ? afterSuccEdge.target : null;
    if (afterSuccEdge) {
      edges = edges.filter((e) => e !== afterSuccEdge);
    }
    edges = [...edges, linkEdge(afterId, nodeId, usedIds)];
    if (afterSucc && afterSucc !== nodeId) {
      edges = [...edges, linkEdge(nodeId, afterSucc, usedIds)];
    }
  }

  return { ...model, edges, startNodeId };
}

/** True when a linear-edge cycle exists among top-level nodes (post-move guard). */
function hasLinearCycle(model: CanvasModel): boolean {
  const topIds = new Set(model.nodes.filter(isTopLevel).map((n) => n.id));
  const adjacency = new Map<string, string[]>();
  for (const id of topIds) {
    adjacency.set(id, []);
  }
  for (const e of model.edges) {
    if (isLinearEdge(e) && topIds.has(e.source) && topIds.has(e.target)) {
      adjacency.get(e.source)!.push(e.target);
    }
  }
  const WHITE = 0;
  const GRAY = 1;
  const BLACK = 2;
  const color = new Map<string, number>();
  for (const id of topIds) {
    color.set(id, WHITE);
  }
  const visit = (start: string): boolean => {
    const stack: Array<{ id: string; childIndex: number }> = [{ id: start, childIndex: 0 }];
    color.set(start, GRAY);
    while (stack.length > 0) {
      const frame = stack[stack.length - 1];
      const children = adjacency.get(frame.id) ?? [];
      if (frame.childIndex >= children.length) {
        color.set(frame.id, BLACK);
        stack.pop();
        continue;
      }
      const next = children[frame.childIndex];
      frame.childIndex += 1;
      const c = color.get(next);
      if (c === GRAY) {
        return true;
      }
      if (c === WHITE) {
        color.set(next, GRAY);
        stack.push({ id: next, childIndex: 0 });
      }
    }
    return false;
  };
  for (const id of topIds) {
    if (color.get(id) === WHITE && visit(id)) {
      return true;
    }
  }
  return false;
}

const NON_TARGET_KINDS = new Set<CanvasNode['kind']>(['DECISION', 'STOP']);

/** A selectable "Runs after" position: a node id, or the Start sentinel. */
export type RunsAfterTarget = {
  value: string;
  isStart: boolean;
};

export type RunsAfterDisabledReason = 'multiplePredecessors' | 'multipleSuccessors' | 'noTargets';

/**
 * Resolved "Runs after" state for the inspector selector.
 *
 * - `editable`  — render the selector; `current` is the selected position and
 *   `targets` the eligible options (always includes the current position).
 * - `disabled`  — render the selector disabled with explanatory copy.
 * - `hidden`    — do not render the selector at all (structural exclusion:
 *   DECISION/RETRY/branch-owned/nested-body nodes).
 */
export type RunsAfterState =
  | { kind: 'editable'; current: string; targets: RunsAfterTarget[] }
  | { kind: 'disabled'; reason: RunsAfterDisabledReason }
  | { kind: 'hidden' };

export function getRunsAfterState(model: CanvasModel, nodeId: string | null): RunsAfterState {
  if (!nodeId) {
    return { kind: 'hidden' };
  }
  const node = model.nodes.find((n) => n.id === nodeId);
  if (!node || !isTopLevel(node)) {
    return { kind: 'hidden' };
  }
  if (node.kind === 'DECISION' || node.kind === 'RETRY') {
    return { kind: 'hidden' };
  }
  const branchOwned = collectBranchNestedIds(model);
  if (branchOwned.has(nodeId)) {
    return { kind: 'hidden' };
  }

  const preds = linearEdgesInto(model, nodeId);
  if (preds.length > 1) {
    return { kind: 'disabled', reason: 'multiplePredecessors' };
  }
  const succs = linearEdgesOutOf(model, nodeId);
  if (succs.length > 1) {
    return { kind: 'disabled', reason: 'multipleSuccessors' };
  }

  const current = preds.length === 1 ? preds[0].source : START_SENTINEL;

  const targets: RunsAfterTarget[] = [{ value: START_SENTINEL, isStart: true }];
  for (const candidate of model.nodes) {
    if (!isTopLevel(candidate) || candidate.id === nodeId) {
      continue;
    }
    if (NON_TARGET_KINDS.has(candidate.kind) || branchOwned.has(candidate.id)) {
      continue;
    }
    if (linearEdgesOutOf(model, candidate.id).length > 1) {
      continue;
    }
    if (hasLinearCycle(repositionAfter(model, nodeId, candidate.id))) {
      continue;
    }
    targets.push({ value: candidate.id, isStart: false });
  }

  // Guarantee the current position is selectable even if it would not otherwise
  // qualify as a move target (controlled-select invariant).
  if (current !== START_SENTINEL && !targets.some((t) => t.value === current)) {
    targets.push({ value: current, isStart: false });
  }

  const hasMove = targets.some((t) => t.value !== current);
  if (!hasMove) {
    return { kind: 'disabled', reason: 'noTargets' };
  }

  return { kind: 'editable', current, targets };
}

/**
 * Nodes eligible to become the workflow's start step via {@link repositionAfter}
 * with {@link START_SENTINEL} — the same operation the inspector's "Runs after"
 * selector performs when its Start option is chosen. Reuses
 * {@link getRunsAfterState}'s own eligibility rules (top-level, not
 * DECISION/RETRY, not branch-owned, at most one linear predecessor/successor)
 * rather than duplicating them. Excludes the current start node itself.
 *
 * One deliberate widening beyond the inspector: a node whose selector is
 * `disabled`/`noTargets` is still offered here. Inside this function that state
 * is only reachable for a second-root node (no linear predecessor, not the
 * current start) with no move target other than Start — and moving exactly that
 * node to Start is still a real, valid operation ({@link repositionAfter} makes
 * it the start step and links the old start after it), even though the
 * inspector, having no *other* position to offer, stays disabled.
 *
 * This widening also makes {@link repositionAfter}'s ordinary detach behavior
 * reachable for a second-root candidate for the first time: if that candidate
 * already has a linear successor of its own (a detached chain, e.g. `X → Y`,
 * built independently of the main scope), moving X to Start severs `X → Y` with
 * no gap closure — X had no predecessor to reconnect to Y, so Y (and anything
 * chained after it) is left without an incoming linear edge. This matches
 * {@link repositionAfter}'s existing single-node-move semantics for every other
 * reposition target (the inspector's own non-Start targets detach the same way);
 * it is not special-cased here. A severed chain surfaces via the unreachable-step
 * validation warning, not silently.
 */
export function startStepCandidateIds(model: CanvasModel): string[] {
  return model.nodes
    .filter((n) => n.id !== model.startNodeId)
    .filter((n) => {
      const state = getRunsAfterState(model, n.id);
      return state.kind === 'editable' || (state.kind === 'disabled' && state.reason === 'noTargets');
    })
    .map((n) => n.id);
}

/**
 * Repair every node-data reference to a just-deleted node id so the model never
 * carries a dangling reference into serialization (where the strict
 * `serializeStepExecutable` throws `Branch target missing`). Pure model→model;
 * apply to the post-removal model (the deleted node already gone, with its edges
 * and `startNodeId` already repaired by the caller). This scrubs node data and
 * `parentNode` only — it does not touch edges or `startNodeId`.
 *
 * Per-field repair (orphaned routing targets are NULLED, never auto-repointed —
 * a guessed target is silent mis-routing, worse than a surfaced validation
 * error): DECISION `cases[].targetNodeId` / `defaultTargetNodeId` → `''`;
 * RETRY `targetNodeId` / `fallbackTargetNodeId` → `''`; REPEAT `bodyNodeIds` →
 * drop the id; a child of the deleted node (deleted REPEAT) → `parentNode`
 * cleared (promoted to top-level).
 */
export function pruneReferences(model: CanvasModel, deletedId: string): CanvasModel {
  const nodes = model.nodes.map((node) => {
    // Promote children of a deleted (REPEAT) node to top-level.
    let next: CanvasNode = node.parentNode === deletedId ? ({ ...node, parentNode: undefined } as CanvasNode) : node;

    switch (next.kind) {
      case 'DECISION': {
        const data = next.data as DecisionNodeData;
        const cases = data.cases.map((c) => (c.targetNodeId === deletedId ? { ...c, targetNodeId: '' } : c));
        const defaultTargetNodeId = data.defaultTargetNodeId === deletedId ? '' : data.defaultTargetNodeId;
        next = { ...next, data: { ...data, cases, defaultTargetNodeId } };
        break;
      }
      case 'RETRY': {
        const data = next.data as RetryNodeData;
        const targetNodeId = data.targetNodeId === deletedId ? '' : data.targetNodeId;
        const fallbackTargetNodeId = data.fallbackTargetNodeId === deletedId ? '' : data.fallbackTargetNodeId;
        next = { ...next, data: { ...data, targetNodeId, fallbackTargetNodeId } };
        break;
      }
      case 'REPEAT': {
        const data = next.data as RepeatNodeData;
        if (data.bodyNodeIds?.includes(deletedId)) {
          next = { ...next, data: { ...data, bodyNodeIds: data.bodyNodeIds.filter((id) => id !== deletedId) } };
        }
        break;
      }
      default:
        break;
    }
    return next;
  });

  return { ...model, nodes };
}

/**
 * True when the edge id refers to an ordinary linear continuation edge that may
 * be split by an edge-insert.
 *
 * Scope-safe (Part A): only TOP-LEVEL, non-branch-owned linear edges qualify.
 * Edge-insert appends a fresh top-level node ({@link onAddStepFromLibrary} sets
 * no `parentNode` and no branch membership), so splicing it into a loop-body or
 * branch-owned chain would misplace it — corrupting loop scope / branch
 * membership and the exported workflow structure. There is no scoped-insertion
 * convention (the inserted node is never assigned a parent or branch), so such
 * edges are excluded outright. This mirrors the top-level/branch-owned scope of
 * {@link getRunsAfterState} and {@link repositionAfter}.
 */
export function isInsertableEdge(model: CanvasModel, edgeId: string): boolean {
  const edge = model.edges.find((e) => e.id === edgeId);
  if (!edge || !isLinearEdge(edge)) {
    return false;
  }
  const byId = new Map(model.nodes.map((n) => [n.id, n] as const));
  const source = byId.get(edge.source);
  const target = byId.get(edge.target);
  if (!source || !target || !isTopLevel(source) || !isTopLevel(target)) {
    return false;
  }
  const branchOwned = collectBranchNestedIds(model);
  if (branchOwned.has(edge.source) || branchOwned.has(edge.target)) {
    return false;
  }
  return true;
}

/**
 * Replace the linear edge `edgeId` (`A → B`) with `A → N` and `N → B`. The new
 * node `newNodeId` must already be appended to the model by the caller. Returns
 * the original edge list unchanged when the edge is missing or not linear.
 */
export function spliceEdgeWithNode(edges: CanvasEdge[], edgeId: string, newNodeId: string): CanvasEdge[] {
  const target = edges.find((e) => e.id === edgeId);
  if (!target || !isLinearEdge(target)) {
    return edges;
  }
  const rest = edges.filter((e) => e.id !== edgeId);
  const usedIds = new Set(rest.map((e) => e.id));
  return [
    ...rest,
    linkEdge(target.source, newNodeId, usedIds),
    linkEdge(newNodeId, target.target, usedIds),
  ];
}

/**
 * Semantic reachability from the scope entry. A node is unreachable iff it is
 * not reachable from `startNodeId` along traversable edges: linear edges,
 * DECISION case/default node-data targets, RETRY target/fallback node-data
 * targets, and the REPEAT body entry (only the designated first body node is
 * reached from the REPEAT; the rest of the body must be reached through
 * traversable edges from there — membership in `bodyNodeIds` alone does not make
 * a body node reachable). Joins (multiple predecessors, branch convergence) are
 * reachable and never flagged.
 */
export function unreachableNodeIds(model: CanvasModel): string[] {
  const entry = model.startNodeId;
  if (!entry || !model.nodes.some((n) => n.id === entry)) {
    return [];
  }
  const known = new Set(model.nodes.map((n) => n.id));
  const adjacency = new Map<string, string[]>();
  const addEdge = (from: string, to: string) => {
    if (!known.has(from) || !known.has(to)) {
      return;
    }
    const list = adjacency.get(from) ?? [];
    list.push(to);
    adjacency.set(from, list);
  };

  for (const e of model.edges) {
    if (isLinearEdge(e)) {
      addEdge(e.source, e.target);
    }
  }
  for (const node of model.nodes) {
    if (node.kind === 'DECISION') {
      const data = node.data as DecisionNodeData;
      for (const c of data.cases) {
        if (c.targetNodeId.trim()) {
          addEdge(node.id, c.targetNodeId.trim());
        }
      }
      if (data.defaultTargetNodeId.trim()) {
        addEdge(node.id, data.defaultTargetNodeId.trim());
      }
    }
    if (node.kind === 'RETRY') {
      const data = node.data as RetryNodeData;
      // RETRY routing is node-data routing (like DECISION): both the retry target
      // and the exhaustion fallback are reachable from the retry node.
      if (data.targetNodeId.trim()) {
        addEdge(node.id, data.targetNodeId.trim());
      }
      if (data.fallbackTargetNodeId.trim()) {
        addEdge(node.id, data.fallbackTargetNodeId.trim());
      }
    }
    if (node.kind === 'REPEAT') {
      const data = node.data as RepeatNodeData;
      const bodyIds = data.bodyNodeIds?.length
        ? data.bodyNodeIds
        : model.nodes.filter((n) => n.parentNode === node.id).map((n) => n.id);
      // Only the loop-body entry (first body node) is reached directly from the
      // REPEAT node; the rest of the body is reached through traversable edges
      // from there. Membership in bodyNodeIds alone is not reachability.
      const bodyEntry = bodyIds[0];
      if (bodyEntry) {
        addEdge(node.id, bodyEntry);
      }
    }
  }

  const reached = new Set<string>([entry]);
  const queue = [entry];
  while (queue.length > 0) {
    const current = queue.shift()!;
    for (const next of adjacency.get(current) ?? []) {
      if (!reached.has(next)) {
        reached.add(next);
        queue.push(next);
      }
    }
  }

  return model.nodes.filter((n) => !reached.has(n.id)).map((n) => n.id);
}
