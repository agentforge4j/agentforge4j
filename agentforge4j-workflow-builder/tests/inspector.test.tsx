// @vitest-environment jsdom
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { ACTION_LABELS, GUIDED_STAGE_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import type { BuilderCapabilities } from '../src/api/types';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('StepConfigPanel', () => {
  it('updates Ask user name in the model', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onUpdate = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={onUpdate}
      />,
    );
    const input = screen.getAllByRole('textbox')[0]!;
    fireEvent.change(input, { target: { value: 'Welcome step' } });
    expect(onUpdate).toHaveBeenCalledWith(id, expect.objectContaining({ name: 'Welcome step' }));
  });

  it('closes inspector on dismiss', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onClose = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={onClose}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getAllByRole('button', { name: /Configure this step/i })[0]!);
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onDelete when Delete step is clicked', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onDelete = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={() => {}}
        onDelete={onDelete}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    expect(onDelete).toHaveBeenCalledWith(id);
  });
});

describe('WorkflowBuilder inspector delete', () => {
  it('asks for confirmation before removing the selected step, then clears selection', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });
    await user.click(screen.getAllByRole('button', { name: ACTION_LABELS.configureStepClose })[0]!);

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(screen.getByRole('dialog', { name: NODE_LABELS.ASK_USER })).toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));

    // Deletion is not immediate: a confirmation dialog gates it.
    const confirmDialog = await screen.findByRole('alertdialog');
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);

    await user.click(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteConfirm }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    expect(container.querySelector('.wf-inspector--open')).toBeNull();
  });

  it('cancelling the confirmation dialog keeps the step', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    const confirmDialog = await screen.findByRole('alertdialog');
    await user.click(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    // Selection/inspector stayed open — deletion never happened.
    expect(screen.getByRole('dialog', { name: NODE_LABELS.ASK_USER })).toBeInTheDocument();
  });

  it('cancelling via Escape also keeps the step AND the inspector open (same semantics as the Cancel button)', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    const confirmDialog = await screen.findByRole('alertdialog');

    // Dispatch on the focused element inside the dialog (as a real keypress would target it) —
    // the dialog's capture-phase window listener must consume it before the inspector's
    // bubble-phase Escape handler ever sees it.
    fireEvent.keyDown(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }), {
      key: 'Escape',
    });

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    // The inspector must NOT have been closed by the same Escape press.
    expect(screen.getByRole('dialog', { name: NODE_LABELS.ASK_USER })).toBeInTheDocument();
  });

  it('focuses Cancel (not Delete) when the confirmation opens, and restores focus on close', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    const deleteButton = screen.getByRole('button', { name: ACTION_LABELS.deleteStep });
    await user.click(deleteButton);

    const confirmDialog = await screen.findByRole('alertdialog');
    // Initial focus on the least destructive action: a stray Enter must not delete.
    expect(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel })).toHaveFocus();

    await user.click(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(deleteButton).toHaveFocus();
  });

  it('confirming a real deletion moves focus to a stable builder target, never to document.body, once the deletion commits', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');
    const builderRoot = screen.getByTestId('workflow-builder');

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    // The opener (this button) is still connected when the dialog itself closes — the
    // regression this guards against is restoring focus to it at close time, only for
    // it to unmount a moment later when the deletion it gated actually commits.
    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    const confirmDialog = await screen.findByRole('alertdialog');
    await user.click(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteConfirm }));

    await waitFor(() => {
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });
    await waitFor(() => {
      // The inspector (and its now-gone "Delete step" button) has unmounted along with
      // the deleted step: the deletion has actually committed by this point.
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(0);

    expect(document.activeElement).toBe(builderRoot);
    expect(document.activeElement).not.toBe(document.body);
  });
});
