// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import { applyEdgeReconnection, mergeModelIntoFlowNodes, resolveNodeDeletionGate, WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import type { CanvasEdge, CanvasModel, CanvasNode } from '../src/model/canvasModel';
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
    const rerouted = next!.edges.find((e) => e.id === 'e-A-B')!;
    expect(rerouted.source).toBe('A');
    expect(rerouted.target).toBe('C');
    // The untouched edge keeps its exact object identity — no field it carries is rebuilt.
    expect(next!.edges.find((e) => e.id === 'e-B-C')).toBe(model.edges[1]);
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
