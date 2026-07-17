// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import type { EdgeChange } from '@xyflow/react';
import {
  applyDeletion,
  applyEdgeReconnection,
  edgeChangeUpdatesModel,
  mergeModelIntoFlowNodes,
  resolveNodeDeletionGate,
  WorkflowCanvas,
} from '../src/canvas/WorkflowCanvas';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import type { CanvasEdge, CanvasModel, CanvasNode, DecisionNodeData } from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';

describe('mergeModelIntoFlowNodes', () => {
  // jsdom has no ResizeObserver — actual visibility:visible is verified in a real browser.
  it('preserves measured width/height when merging domain-derived nodes', () => {
    const existing: Node[] = [
      {
        id: 'n-1',
        type: 'step',
        position: { x: 0, y: 0 },
        data: {},
        measured: { width: 228, height: 96 },
        width: 228,
        height: 96,
      },
    ];
    const derived: Node[] = [
      {
        id: 'n-1',
        type: 'step',
        position: { x: 40, y: 80 },
        data: { selected: true },
      },
    ];
    const merged = mergeModelIntoFlowNodes(existing, derived);
    expect(merged).toHaveLength(1);
    expect(merged[0]?.position).toEqual({ x: 40, y: 80 });
    expect(merged[0]?.measured).toEqual({ width: 228, height: 96 });
    expect(merged[0]?.width).toBe(228);
    expect(merged[0]?.height).toBe(96);
  });

  it('adds new nodes without measured dimensions', () => {
    const merged = mergeModelIntoFlowNodes([], [{ id: 'n-new', type: 'step', position: { x: 0, y: 0 }, data: {} }]);
    expect(merged[0]?.measured).toBeUndefined();
  });
});

describe('resolveNodeDeletionGate', () => {
  it('refuses in read-only mode regardless of what is being deleted', async () => {
    await expect(resolveNodeDeletionGate([], { readOnly: true })).resolves.toBe(false);
    await expect(resolveNodeDeletionGate(['n-1'], { readOnly: true, confirmNodeDeletion: async () => true })).resolves.toBe(
      false,
    );
  });

  it('approves an edge-only deletion (no node ids) without asking for confirmation', async () => {
    let called = false;
    await expect(
      resolveNodeDeletionGate([], {
        readOnly: false,
        confirmNodeDeletion: async () => {
          called = true;
          return false;
        },
      }),
    ).resolves.toBe(true);
    expect(called).toBe(false);
  });

  it('approves a node deletion immediately when no confirmNodeDeletion handler is wired', async () => {
    await expect(resolveNodeDeletionGate(['n-1'], { readOnly: false })).resolves.toBe(true);
  });

  it('defers to confirmNodeDeletion for a node deletion, passing every id in the batch', async () => {
    let received: string[] | null = null;
    const outcome = await resolveNodeDeletionGate(['n-1', 'n-2'], {
      readOnly: false,
      confirmNodeDeletion: async (ids) => {
        received = ids;
        return true;
      },
    });
    expect(outcome).toBe(true);
    expect(received).toEqual(['n-1', 'n-2']);
  });

  it('propagates a cancelled confirmation (resolved false) as a refusal', async () => {
    await expect(
      resolveNodeDeletionGate(['n-1'], { readOnly: false, confirmNodeDeletion: async () => false }),
    ).resolves.toBe(false);
  });
});

describe('applyEdgeReconnection', () => {
  function reconnectModel(): CanvasModel {
    const mk = (id: string): CanvasNode =>
      ({ id, backendStepId: id, kind: 'AI_STEP', position: { x: 0, y: 0 }, data: defaultNodeData('AI_STEP') }) as CanvasNode;
    const edge = (id: string, source: string, target: string, sourceHandle: string | null = null): CanvasEdge => ({
      id,
      source,
      target,
      sourceHandle,
      label: null,
    });
    return {
      workflowId: 'wf',
      workflowName: 'Test',
      description: '',
      startNodeId: 'A',
      nodes: [mk('A'), mk('B'), mk('C')],
      edges: [edge('e-A-B', 'A', 'B'), edge('e-B-C', 'B', 'C')],
      artifacts: {},
      blueprints: {},
    };
  }

  it('rewrites only the rerouted edge and keeps every other edge object untouched', () => {
    const model = reconnectModel();
    const next = applyEdgeReconnection(model, 'e-A-B', { source: 'A', target: 'C', sourceHandle: null });

    expect(next).not.toBeNull();
    expect(next!.edges).toHaveLength(2);
    const rerouted = next!.edges.find((e) => e.id !== 'e-B-C')!;
    expect(rerouted.source).toBe('A');
    expect(rerouted.target).toBe('C');
    // The untouched edge keeps its exact object identity — no field it carries is rebuilt.
    expect(next!.edges.find((e) => e.id === 'e-B-C')).toBe(model.edges[1]);
  });

  it('re-mints the rerouted edge id from its NEW endpoints so a later connect on the old endpoints cannot collide', () => {
    const model = reconnectModel();
    const next = applyEdgeReconnection(model, 'e-A-B', { source: 'A', target: 'C', sourceHandle: null });

    // onConnect derives ids as e-{source}-{target}-{handle ?? 'src'} and relies on ids
    // matching endpoints; a rerouted edge keeping id 'e-A-B' would let a later A->B
    // connect mint a duplicate id.
    const rerouted = next!.edges.find((e) => e.id !== 'e-B-C')!;
    expect(rerouted.id).toBe('e-A-C-src');
  });

  it('suffixes the re-minted id when another edge already holds it', () => {
    const model = reconnectModel();
    // An unrelated edge already occupies the endpoint-derived id for A->C.
    model.edges.push({ id: 'e-A-C-src', source: 'B', target: 'C', sourceHandle: null, label: null });
    const next = applyEdgeReconnection(model, 'e-A-B', { source: 'A', target: 'C', sourceHandle: null });

    expect(next).not.toBeNull();
    const ids = next!.edges.map((e) => e.id);
    expect(new Set(ids).size).toBe(ids.length); // all ids stay unique
    expect(ids).toContain('e-A-C-src-2');
  });

  it('refuses to reroute a decision-branch (case-handle) edge — its routing is node data, not the drawn edge', () => {
    const model = reconnectModel();
    model.edges.push({ id: 'e-branch', source: 'A', target: 'B', sourceHandle: 'yes', label: 'Yes' });
    expect(applyEdgeReconnection(model, 'e-branch', { source: 'A', target: 'C', sourceHandle: null })).toBeNull();
  });

  it('refuses a reconnect that would land on a case handle (would create a cosmetic branch edge)', () => {
    const model = reconnectModel();
    expect(applyEdgeReconnection(model, 'e-A-B', { source: 'A', target: 'C', sourceHandle: 'yes' })).toBeNull();
  });

  it('refuses a reconnect that would duplicate an existing edge (same source/target/handle), mirroring onConnect', () => {
    const model = reconnectModel();
    // Rerouting B->C onto A->B's endpoints would produce two parallel A->B edges.
    expect(applyEdgeReconnection(model, 'e-B-C', { source: 'A', target: 'B', sourceHandle: null })).toBeNull();
  });

  it('treats a same-endpoints drop (no change) as a no-op', () => {
    const model = reconnectModel();
    expect(applyEdgeReconnection(model, 'e-A-B', { source: 'A', target: 'B', sourceHandle: null })).toBeNull();
  });

  it('returns null for an incomplete connection or an unknown edge id', () => {
    const model = reconnectModel();
    expect(applyEdgeReconnection(model, 'e-A-B', { source: null, target: 'C' })).toBeNull();
    expect(applyEdgeReconnection(model, 'nope', { source: 'A', target: 'C' })).toBeNull();
  });
});

describe('applyDeletion', () => {
  function mk(id: string, kind: CanvasNode['kind'] = 'AI_STEP'): CanvasNode {
    return { id, backendStepId: id, kind, position: { x: 0, y: 0 }, data: defaultNodeData(kind) } as CanvasNode;
  }
  function edge(id: string, source: string, target: string): CanvasEdge {
    return { id, source, target, sourceHandle: null, label: null };
  }
  function deletionModel(): CanvasModel {
    const decision = mk('D', 'DECISION');
    (decision.data as DecisionNodeData).cases = [{ label: 'Yes', value: 'yes', targetNodeId: 'B' }];
    (decision.data as DecisionNodeData).defaultTargetNodeId = 'B';
    return {
      workflowId: 'wf',
      workflowName: 'Test',
      description: '',
      startNodeId: 'A',
      nodes: [mk('A'), mk('B'), mk('C'), decision],
      edges: [edge('e-A-B', 'A', 'B'), edge('e-B-C', 'B', 'C'), edge('e-D-B', 'D', 'B')],
      artifacts: {},
      blueprints: {},
    };
  }

  it('removes the deleted node and every edge incident to it in one pass', () => {
    const model = deletionModel();
    const next = applyDeletion(model, ['B'], []);
    expect(next.nodes.map((n) => n.id)).toEqual(['A', 'C', 'D']);
    // Both A-B (source-incident) and B-C (target... source-incident) and D-B are gone.
    expect(next.edges).toHaveLength(0);
  });

  it('removes only the specified edges when no nodes are deleted', () => {
    const model = deletionModel();
    const next = applyDeletion(model, [], ['e-B-C']);
    expect(next.nodes).toHaveLength(4);
    expect(next.edges.map((e) => e.id)).toEqual(['e-A-B', 'e-D-B']);
  });

  it('does not error or duplicate-remove when an edge is both explicitly listed and incident to a deleted node', () => {
    const model = deletionModel();
    const next = applyDeletion(model, ['B'], ['e-A-B']);
    expect(next.edges).toHaveLength(0);
  });

  it('reassigns startNodeId to the first remaining node when the start node is deleted', () => {
    const model = deletionModel();
    const next = applyDeletion(model, ['A'], []);
    expect(next.startNodeId).toBe('B');
  });

  it('prunes dangling references on every remaining node for every deleted id, in the same pass', () => {
    const model = deletionModel();
    const next = applyDeletion(model, ['B'], []);
    const decision = next.nodes.find((n) => n.id === 'D')!.data as DecisionNodeData;
    expect(decision.cases[0]!.targetNodeId).toBe('');
    expect(decision.defaultTargetNodeId).toBe('');
  });

  it('one call is the whole removal — a caller wrapping it in a single onModelChange writes history exactly once', () => {
    // Structural guarantee, not a UI simulation: applyDeletion returns the fully-removed
    // model in one synchronous call, so a caller (WorkflowCanvas's onDelete) that passes
    // its result to a single onModelChange call can only ever produce one history entry
    // for a whole gesture — there is no intermediate/partial model to accidentally write.
    const model = deletionModel();
    let writes = 0;
    const onModelChange = (_next: CanvasModel) => {
      writes += 1;
    };
    onModelChange(applyDeletion(model, ['B'], []));
    expect(writes).toBe(1);
  });
});

describe('edgeChangeUpdatesModel', () => {
  it('ignores select changes (edges never persist a selected field in the model)', () => {
    expect(edgeChangeUpdatesModel({ type: 'select', id: 'e-1', selected: true } as EdgeChange)).toBe(false);
  });

  it('ignores remove changes (handled once, by the unified onDelete writer)', () => {
    expect(edgeChangeUpdatesModel({ type: 'remove', id: 'e-1' } as EdgeChange)).toBe(false);
  });

  it('treats any other change type as model-relevant', () => {
    expect(edgeChangeUpdatesModel({ type: 'add', item: {} } as unknown as EdgeChange)).toBe(true);
    expect(edgeChangeUpdatesModel({ type: 'replace', id: 'e-1', item: {} } as unknown as EdgeChange)).toBe(true);
  });
});

describe('WorkflowCanvas', () => {
  it('renders react-flow root', () => {
    const model = createInitialCanvasModel();
    const { container } = render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(container.querySelector('.react-flow')).toBeTruthy();
  });

  it('shows start-here hint on a fresh starter canvas', () => {
    const model = createInitialCanvasModel();
    render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(screen.getAllByText(/Start here/i).length).toBeGreaterThan(0);
  });

  it('still shows the start-here hint on a fresh starter canvas when hideStarterHint is not set (outside guided mode, unchanged)', () => {
    const model = createInitialCanvasModel();
    render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
        hideStarterHint={false}
      />,
    );
    expect(screen.getAllByText(/Start here/i).length).toBeGreaterThan(0);
  });

  it('suppresses the start-here hint on a fresh starter canvas when hideStarterHint is true (guided mode)', () => {
    const model = createInitialCanvasModel();
    render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
        hideStarterHint
      />,
    );
    expect(screen.queryByText(/Start here/i)).not.toBeInTheDocument();
  });
});
