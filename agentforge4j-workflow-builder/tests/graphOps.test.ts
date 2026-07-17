import { describe, expect, it } from 'vitest';
import {
  getRunsAfterState,
  isInsertableEdge,
  isInsideLoopBody,
  pruneReferences,
  repositionAfter,
  spliceEdgeWithNode,
  START_SENTINEL,
  unreachableNodeIds,
} from '../src/model/graphOps';
import type {
  CanvasEdge,
  CanvasModel,
  CanvasNode,
  DecisionNodeData,
  RepeatNodeData,
  RetryNodeData,
} from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';

function node(id: string, kind: CanvasNode['kind'] = 'AI_STEP'): CanvasNode {
  return {
    id,
    backendStepId: id,
    kind,
    position: { x: 0, y: 0 },
    data: defaultNodeData(kind),
  } as CanvasNode;
}

function bodyNode(id: string, parentNode: string): CanvasNode {
  return { id, backendStepId: id, kind: 'AI_STEP', position: { x: 0, y: 0 }, parentNode, data: defaultNodeData('AI_STEP') } as CanvasNode;
}

function repeatNode(id: string, bodyNodeIds: string[]): CanvasNode {
  const repeat = node(id, 'REPEAT');
  (repeat.data as RepeatNodeData).bodyNodeIds = bodyNodeIds;
  return repeat;
}

function linEdge(source: string, target: string): CanvasEdge {
  return { id: `e-${source}-${target}`, source, target, sourceHandle: null, label: null };
}

function model(nodes: CanvasNode[], edges: CanvasEdge[], startNodeId: string | null): CanvasModel {
  return {
    workflowId: 'wf',
    workflowName: 'Test',
    description: '',
    startNodeId,
    nodes,
    edges,
    artifacts: {},
    blueprints: {},
  };
}

/** Map of source → targets over linear edges only. */
function linearChain(m: CanvasModel): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const e of m.edges) {
    if (e.sourceHandle == null) {
      (out[e.source] ??= []).push(e.target);
    }
  }
  return out;
}

describe('repositionAfter', () => {
  it('moves a tail node to run after an earlier node, closing the old gap', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    const next = repositionAfter(m, 'C', 'A');
    const chain = linearChain(next);
    expect(chain.A).toEqual(['C']);
    expect(chain.C).toEqual(['B']);
    expect(chain.B ?? []).toEqual([]);
    expect(next.startNodeId).toBe('A');
  });

  it('repositions to Start, making the node the scope entry', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    const next = repositionAfter(m, 'C', START_SENTINEL);
    const chain = linearChain(next);
    expect(next.startNodeId).toBe('C');
    expect(chain.C).toEqual(['A']);
    expect(chain.A).toEqual(['B']);
  });

  it('moving the scope-entry after a later node promotes its successor to entry', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    const next = repositionAfter(m, 'A', 'C');
    const chain = linearChain(next);
    expect(next.startNodeId).toBe('B');
    expect(chain.B).toEqual(['C']);
    expect(chain.C).toEqual(['A']);
  });

  it('moving a node after its current predecessor is a no-op chain', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    const next = repositionAfter(m, 'B', 'A');
    const chain = linearChain(next);
    expect(chain.A).toEqual(['B']);
    expect(chain.B).toEqual(['C']);
  });

  it('is a safe no-op for a single isolated node repositioned to Start', () => {
    const m = model([node('A')], [], 'A');
    const next = repositionAfter(m, 'A', START_SENTINEL);
    expect(next.startNodeId).toBe('A');
    expect(next.edges).toEqual([]);
  });

  it('moves a tail node (predecessor but no successor) to Start, closing the gap', () => {
    const m = model([node('A'), node('B')], [linEdge('A', 'B')], 'A');
    const next = repositionAfter(m, 'B', START_SENTINEL);
    const chain = linearChain(next);
    expect(next.startNodeId).toBe('B');
    expect(chain.B).toEqual(['A']);
    expect(chain.A ?? []).toEqual([]);
  });
});

describe('getRunsAfterState', () => {
  it('is editable for an ordinary middle node with single pred/succ', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    const state = getRunsAfterState(m, 'B');
    expect(state.kind).toBe('editable');
    if (state.kind === 'editable') {
      expect(state.current).toBe('A');
      const values = state.targets.map((t) => t.value);
      expect(values).toContain(START_SENTINEL);
      // Pre-move reachability must NOT block: C (current successor) is allowed.
      expect(values).toContain('C');
    }
  });

  it('is disabled with multiplePredecessors when a node has two linear predecessors', () => {
    const m = model(
      [node('A'), node('B'), node('C')],
      [linEdge('A', 'C'), linEdge('B', 'C')],
      'A',
    );
    const state = getRunsAfterState(m, 'C');
    expect(state).toEqual({ kind: 'disabled', reason: 'multiplePredecessors' });
  });

  it('is disabled with multipleSuccessors when a node has two linear successors', () => {
    const m = model(
      [node('A'), node('B'), node('C')],
      [linEdge('A', 'B'), linEdge('A', 'C')],
      'A',
    );
    const state = getRunsAfterState(m, 'A');
    expect(state).toEqual({ kind: 'disabled', reason: 'multipleSuccessors' });
  });

  it('is disabled with noTargets for a lone node', () => {
    const m = model([node('A')], [], 'A');
    expect(getRunsAfterState(m, 'A')).toEqual({ kind: 'disabled', reason: 'noTargets' });
  });

  it('is hidden for DECISION and RETRY selected nodes', () => {
    const m = model([node('A'), node('D', 'DECISION'), node('R', 'RETRY')], [linEdge('A', 'D')], 'A');
    expect(getRunsAfterState(m, 'D').kind).toBe('hidden');
    expect(getRunsAfterState(m, 'R').kind).toBe('hidden');
  });

  it('is hidden for a branch-owned (DECISION-target) node', () => {
    const decision = node('D', 'DECISION');
    (decision.data as DecisionNodeData).cases[0].targetNodeId = 'X';
    (decision.data as DecisionNodeData).defaultTargetNodeId = 'Y';
    const m = model([node('A'), decision, node('X'), node('Y')], [linEdge('A', 'D')], 'A');
    expect(getRunsAfterState(m, 'X').kind).toBe('hidden');
  });

  it('excludes a target that would create a post-move cycle', () => {
    // Pre-existing back edge C -> A: moving B after C would form A->C->B->A.
    const cyclic = model(
      [node('A'), node('B'), node('C')],
      [linEdge('A', 'B'), linEdge('B', 'C'), linEdge('C', 'A')],
      'A',
    );
    const state = getRunsAfterState(cyclic, 'B');
    // B has a single pred (A) and single succ (C) so it is editable...
    expect(state.kind).toBe('editable');
    if (state.kind === 'editable') {
      // ...but C must be rejected as a target by the cycle guard.
      expect(state.targets.map((t) => t.value)).not.toContain('C');
    }
  });
});

describe('spliceEdgeWithNode / isInsertableEdge', () => {
  it('splits a linear edge A->B into A->N and N->B', () => {
    const edges = [linEdge('A', 'B')];
    const next = spliceEdgeWithNode(edges, 'e-A-B', 'N');
    const pairs = next.map((e) => `${e.source}->${e.target}`).sort();
    expect(pairs).toEqual(['A->N', 'N->B']);
    expect(next.every((e) => e.sourceHandle == null)).toBe(true);
  });

  it('treats null-sourceHandle edges as insertable and decision overlay edges as not', () => {
    const overlay: CanvasEdge = { id: 'e-D-X-yes', source: 'D', target: 'X', sourceHandle: 'yes', label: null };
    const m = model([node('A'), node('B'), node('D', 'DECISION'), node('X')], [linEdge('A', 'B'), overlay], 'A');
    expect(isInsertableEdge(m, 'e-A-B')).toBe(true);
    expect(isInsertableEdge(m, 'e-D-X-yes')).toBe(false);
  });

  it('does NOT treat a loop-body linear edge as insertable (would corrupt loop scope)', () => {
    // A → R(repeat) at top level; B1 → B2 is a linear edge INSIDE the loop body.
    const m = model(
      [node('A'), repeatNode('R', ['B1', 'B2']), bodyNode('B1', 'R'), bodyNode('B2', 'R')],
      [linEdge('A', 'R'), linEdge('B1', 'B2')],
      'A',
    );
    // The top-level edge remains insertable...
    expect(isInsertableEdge(m, 'e-A-R')).toBe(true);
    // ...but splicing a fresh top-level node into the loop body is forbidden:
    // both endpoints carry parentNode === 'R'.
    expect(isInsertableEdge(m, 'e-B1-B2')).toBe(false);
  });

  it('does NOT treat a branch-owned linear edge as insertable', () => {
    // Decision D routes case → X, default (merge) → M. The X → Y edge lies inside
    // the branch chain (X, Y are branch-owned; M is the merge point, not owned).
    const decision = node('D', 'DECISION');
    (decision.data as DecisionNodeData).cases = [{ label: 'yes', value: 'yes', targetNodeId: 'X' }];
    (decision.data as DecisionNodeData).defaultTargetNodeId = 'M';
    const m = model(
      [node('A'), decision, node('X'), node('Y'), node('M')],
      [linEdge('A', 'D'), linEdge('X', 'Y'), linEdge('Y', 'M')],
      'A',
    );
    expect(isInsertableEdge(m, 'e-X-Y')).toBe(false);
    // A top-level edge unrelated to the branch stays insertable.
    expect(isInsertableEdge(m, 'e-A-D')).toBe(true);
  });

  it('does not corrupt exported structure: a loop-body edge is rejected so no top-level node is injected mid-body', () => {
    const m = model(
      [node('A'), repeatNode('R', ['B1', 'B2']), bodyNode('B1', 'R'), bodyNode('B2', 'R')],
      [linEdge('A', 'R'), linEdge('B1', 'B2')],
      'A',
    );
    // The runtime insert path only splices when isInsertableEdge is true; for a
    // body edge it is false, so the body edge list is never split. The splice
    // helper itself is unconditional — the gate above is the protection.
    expect(isInsertableEdge(m, 'e-B1-B2')).toBe(false);
    const spliced = spliceEdgeWithNode(m.edges, 'e-B1-B2', 'N');
    expect(spliced.some((e) => e.source === 'B1' && e.target === 'N')).toBe(true);
  });
});

describe('unreachableNodeIds', () => {
  it('returns nothing for a fully connected chain', () => {
    const m = model([node('A'), node('B'), node('C')], [linEdge('A', 'B'), linEdge('B', 'C')], 'A');
    expect(unreachableNodeIds(m)).toEqual([]);
  });

  it('returns [] when there is no start node (null-guard early return)', () => {
    const m = model([node('A'), node('B')], [linEdge('A', 'B')], null);
    expect(unreachableNodeIds(m)).toEqual([]);
  });

  it('flags an isolated node but not branch targets reached via node data', () => {
    const decision = node('D', 'DECISION');
    (decision.data as DecisionNodeData).cases[0].targetNodeId = 'X';
    const m = model(
      [node('A'), decision, node('X'), node('ORPHAN')],
      [linEdge('A', 'D')],
      'A',
    );
    const unreachable = unreachableNodeIds(m);
    expect(unreachable).toContain('ORPHAN');
    expect(unreachable).not.toContain('X');
  });

  it('does not warn on a multi-predecessor join', () => {
    const m = model(
      [node('A'), node('B'), node('C')],
      [linEdge('A', 'C'), linEdge('B', 'C'), linEdge('A', 'B')],
      'A',
    );
    expect(unreachableNodeIds(m)).toEqual([]);
  });

  it('reaches a RETRY target via node-data routing', () => {
    const retry = node('R', 'RETRY');
    (retry.data as RetryNodeData).targetNodeId = 'T';
    // T is reachable ONLY through the retry target, not via any linear edge.
    const m = model([node('A'), retry, node('T')], [linEdge('A', 'R')], 'A');
    expect(unreachableNodeIds(m)).not.toContain('T');
  });

  it('reaches a RETRY fallback target via node-data routing', () => {
    const retry = node('R', 'RETRY');
    (retry.data as RetryNodeData).fallbackTargetNodeId = 'F';
    // F is reachable ONLY through the retry exhaustion fallback.
    const m = model([node('A'), retry, node('F')], [linEdge('A', 'R')], 'A');
    expect(unreachableNodeIds(m)).not.toContain('F');
  });

  it('flags a node only when it is unreachable through linear, decision, repeat AND retry routing', () => {
    const retry = node('R', 'RETRY');
    (retry.data as RetryNodeData).targetNodeId = 'T';
    (retry.data as RetryNodeData).fallbackTargetNodeId = 'F';
    const m = model(
      [node('A'), retry, node('T'), node('F'), node('ORPHAN')],
      [linEdge('A', 'R')],
      'A',
    );
    const unreachable = unreachableNodeIds(m);
    expect(unreachable).not.toContain('T'); // reached via retry target
    expect(unreachable).not.toContain('F'); // reached via retry fallback
    expect(unreachable).toContain('ORPHAN'); // reached by nothing
  });

  it('does not warn when a loop-body chain is connected from the entry', () => {
    const m = model(
      [node('A'), repeatNode('R', ['B1', 'B2']), bodyNode('B1', 'R'), bodyNode('B2', 'R')],
      [linEdge('A', 'R'), linEdge('B1', 'B2')],
      'A',
    );
    expect(unreachableNodeIds(m)).toEqual([]);
  });

  it('flags a body member listed in bodyNodeIds but not connected from the entry', () => {
    const m = model(
      [node('A'), repeatNode('R', ['B1', 'B2']), bodyNode('B1', 'R'), bodyNode('B2', 'R')],
      [linEdge('A', 'R')],
      'A',
    );
    const unreachable = unreachableNodeIds(m);
    expect(unreachable).toContain('B2');
    expect(unreachable).not.toContain('B1');
    expect(unreachable).not.toContain('R');
  });

  it('only reaches a nested body via its entry chain, not by membership alone', () => {
    const m = model(
      [
        node('A'),
        repeatNode('OUTER', ['INNER', 'X']),
        repeatNode('INNER', ['I1', 'I2']),
        bodyNode('X', 'OUTER'),
        bodyNode('I1', 'INNER'),
        bodyNode('I2', 'INNER'),
      ],
      [linEdge('A', 'OUTER'), linEdge('I1', 'I2')],
      'A',
    );
    // INNER (parentNode OUTER) needs its parent set for membership context.
    const inner = m.nodes.find((n) => n.id === 'INNER')!;
    inner.parentNode = 'OUTER';

    const unreachable = unreachableNodeIds(m);
    expect(unreachable).toContain('X');
    expect(unreachable).not.toContain('INNER');
    expect(unreachable).not.toContain('I1');
    expect(unreachable).not.toContain('I2');
  });
});

describe('pruneReferences', () => {
  it('nulls a DECISION case target and default target that point at the deleted node', () => {
    const decision = node('D', 'DECISION');
    (decision.data as DecisionNodeData).cases = [
      { label: 'yes', value: 'yes', targetNodeId: 'X' },
      { label: 'no', value: 'no', targetNodeId: 'Y' },
    ];
    (decision.data as DecisionNodeData).defaultTargetNodeId = 'X';
    // Caller has already removed X; pruneReferences scrubs the survivors.
    const m = model([node('A'), decision, node('Y')], [], 'A');
    const next = pruneReferences(m, 'X');
    const d = next.nodes.find((n) => n.id === 'D')!.data as DecisionNodeData;
    expect(d.cases[0].targetNodeId).toBe('');
    expect(d.cases[1].targetNodeId).toBe('Y'); // unrelated target untouched
    expect(d.defaultTargetNodeId).toBe('');
  });

  it('nulls RETRY target and fallback that point at the deleted node', () => {
    const retry = node('R', 'RETRY');
    (retry.data as RetryNodeData).targetNodeId = 'X';
    (retry.data as RetryNodeData).fallbackTargetNodeId = 'X';
    const m = model([node('A'), retry], [], 'A');
    const d = pruneReferences(m, 'X').nodes.find((n) => n.id === 'R')!.data as RetryNodeData;
    expect(d.targetNodeId).toBe('');
    expect(d.fallbackTargetNodeId).toBe('');
  });

  it('leaves RETRY targets that point elsewhere untouched', () => {
    const retry = node('R', 'RETRY');
    (retry.data as RetryNodeData).targetNodeId = 'A';
    (retry.data as RetryNodeData).fallbackTargetNodeId = 'B';
    const m = model([node('A'), node('B'), retry], [], 'A');
    const d = pruneReferences(m, 'X').nodes.find((n) => n.id === 'R')!.data as RetryNodeData;
    expect(d.targetNodeId).toBe('A');
    expect(d.fallbackTargetNodeId).toBe('B');
  });

  it('removes the deleted id from a REPEAT bodyNodeIds list', () => {
    const m = model([node('A'), repeatNode('REP', ['B1', 'B2'])], [], 'A');
    const d = pruneReferences(m, 'B1').nodes.find((n) => n.id === 'REP')!.data as RepeatNodeData;
    expect(d.bodyNodeIds).toEqual(['B2']);
  });

  it('promotes children of a deleted REPEAT node to top-level (clears parentNode)', () => {
    // Caller has already removed REP; its former children remain.
    const m = model([node('A'), bodyNode('B1', 'REP'), bodyNode('B2', 'REP')], [], 'A');
    const next = pruneReferences(m, 'REP');
    expect(next.nodes.find((n) => n.id === 'B1')!.parentNode).toBeUndefined();
    expect(next.nodes.find((n) => n.id === 'B2')!.parentNode).toBeUndefined();
  });

  it('leaves a model with no references to the deleted node unchanged', () => {
    const m = model([node('A'), node('B', 'AI_STEP')], [linEdge('A', 'B')], 'A');
    const next = pruneReferences(m, 'GONE');
    expect(next.nodes).toEqual(m.nodes);
  });
});

describe('isInsideLoopBody', () => {
  it('is true for a node whose parent is a REPEAT node', () => {
    const repeat = repeatNode('REP', ['B1']);
    const m = model([node('A'), repeat, bodyNode('B1', 'REP')], [], 'A');
    const b1 = m.nodes.find((n) => n.id === 'B1')!;
    expect(isInsideLoopBody(m, b1)).toBe(true);
  });

  it('is false for a top-level node with no parentNode', () => {
    const m = model([node('A'), node('B')], [linEdge('A', 'B')], 'A');
    const b = m.nodes.find((n) => n.id === 'B')!;
    expect(isInsideLoopBody(m, b)).toBe(false);
  });

  it('is false when parentNode points at a non-REPEAT (or missing) node', () => {
    const m = model([node('A'), node('DEC', 'DECISION'), bodyNode('B1', 'DEC')], [], 'A');
    const b1 = m.nodes.find((n) => n.id === 'B1')!;
    expect(isInsideLoopBody(m, b1)).toBe(false);

    const dangling = { ...bodyNode('B2', 'GONE') };
    expect(isInsideLoopBody(model([node('A'), dangling], [], 'A'), dangling)).toBe(false);
  });
});
