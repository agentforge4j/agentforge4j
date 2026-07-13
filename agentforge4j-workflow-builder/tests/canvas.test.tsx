// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import { mergeModelIntoFlowNodes, resolveNodeDeletionGate, WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';

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
