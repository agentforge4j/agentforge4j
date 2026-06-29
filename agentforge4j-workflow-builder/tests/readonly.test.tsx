// @vitest-environment jsdom
import { act, render, renderHook, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { createInitialCanvasModel, useCanvasState } from '../src/hooks/useCanvasState';
import { ACTION_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import type { BuilderCapabilities } from '../src/api/types';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('useCanvasState read-only chokepoint', () => {
  it('rejects mutations at the setter when read-only', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel(), true));
    const before = result.current.model;
    const firstId = before.nodes[0]!.id;

    act(() => {
      result.current.updateNodeData(firstId, { name: 'Changed' } as never);
    });
    act(() => {
      result.current.appendNode('AI_STEP', { x: 0, y: 0 });
    });
    act(() => {
      result.current.setModel((m) => ({ ...m, workflowName: 'mutated' }));
    });

    expect(result.current.model).toBe(before);
    expect(result.current.isDirty).toBe(false);
  });

  it('allows mutations when editable (default)', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel(), false));
    act(() => {
      result.current.appendNode('AI_STEP', { x: 0, y: 0 });
    });
    expect(result.current.model.nodes.length).toBe(2);
    expect(result.current.isDirty).toBe(true);
  });
});

describe('WorkflowBuilder read-only mode', () => {
  it('marks the root read-only and shows the badge', () => {
    render(<WorkflowBuilder capabilities={allDisabled} mode="readOnly" />);
    expect(screen.getByTestId('workflow-builder')).toHaveAttribute('aria-readonly', 'true');
    expect(screen.getByTestId('workflow-builder-readonly-badge')).toBeInTheDocument();
  });

  it('overrides a mutating capability (save hidden) but keeps export available', () => {
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, save: true, export: true }}
        actions={{ save: vi.fn() }}
        mode="readOnly"
      />,
    );
    expect(screen.queryByTestId('workflow-builder-save')).not.toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-export')).toBeInTheDocument();
  });

  it('hides the step palette and mode toggle in read-only mode', () => {
    render(<WorkflowBuilder capabilities={allDisabled} mode="readOnly" />);
    expect(screen.queryByRole('button', { name: NODE_LABELS.AI_STEP })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: ACTION_LABELS.guidedMode })).not.toBeInTheDocument();
  });

  it('renders the name field as read-only', () => {
    render(<WorkflowBuilder capabilities={allDisabled} mode="readOnly" />);
    expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveAttribute('readonly');
  });

  it('keeps the palette in the default (editable) mode', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.getByTestId('workflow-builder')).not.toHaveAttribute('aria-readonly');
    expect(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP }).length).toBeGreaterThan(0);
  });
});
