// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities } from '../src/api/types';
import { ACTION_LABELS, GUIDED_STAGE_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('WorkflowBuilder undo/redo toolbar', () => {
  it('starts with Undo and Redo disabled', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.getByTestId('workflow-builder-undo')).toBeDisabled();
    expect(screen.getByTestId('workflow-builder-redo')).toBeDisabled();
  });

  it('enables Undo after a structural change, and Undo/Redo round-trip the node count', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });

    const undoButton = screen.getByTestId('workflow-builder-undo');
    const redoButton = screen.getByTestId('workflow-builder-redo');
    expect(undoButton).not.toBeDisabled();
    expect(redoButton).toBeDisabled();

    await user.click(undoButton);
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    expect(redoButton).not.toBeDisabled();

    await user.click(redoButton);
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
  });

  it('Ctrl+Z / Ctrl+Shift+Z keyboard shortcuts undo and redo a structural change', async () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');
    const user = userEvent.setup();

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });

    // Dispatched on an element inside the builder root: the shortcut listener sits on
    // window but only accepts keydowns originating within this builder instance.
    fireEvent.keyDown(canvas, { key: 'z', ctrlKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    fireEvent.keyDown(canvas, { key: 'Z', ctrlKey: true, shiftKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
  });

  it('ignores Ctrl+Z originating outside the builder root (host-page undo is not hijacked)', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });

    // A keydown targeting the host page (document.body) must leave the builder's
    // history untouched — an embedding host's own Ctrl+Z is not this builder's.
    fireEvent.keyDown(document.body, { key: 'z', ctrlKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    expect(screen.getByTestId('workflow-builder-undo')).not.toBeDisabled();
  });

  it('Ctrl+Y redoes like Ctrl+Shift+Z', async () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');
    const user = userEvent.setup();

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });

    fireEvent.keyDown(canvas, { key: 'z', ctrlKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    fireEvent.keyDown(canvas, { key: 'y', ctrlKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
  });

  it('ignores Ctrl+Alt+Z (AltGr on Windows layouts) — character keys must not trigger undo', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });

    fireEvent.keyDown(canvas, { key: 'z', ctrlKey: true, altKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    expect(screen.getByTestId('workflow-builder-undo')).not.toBeDisabled();
  });

  it('freezes undo/redo shortcuts while the delete confirmation is pending', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    // Build up real undo history first — an unguarded Ctrl+Z during the dialog would
    // pop this step-add and change the node count.
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
    });
    expect(screen.getByTestId('workflow-builder-undo')).not.toBeDisabled();

    // Select the input step via the guided stepper (jsdom cannot click-select canvas
    // nodes — see the other inspector-driven tests), then request its deletion.
    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    const nodesBefore = canvas.querySelectorAll('.react-flow__node').length;
    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    const confirmDialog = await screen.findByRole('alertdialog');

    // Ctrl+Z targeting the dialog (inside the builder root) must NOT mutate the model
    // under the pending question.
    fireEvent.keyDown(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }), {
      key: 'z',
      ctrlKey: true,
    });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(nodesBefore);
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    await user.click(within(confirmDialog).getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(nodesBefore);
  });

  it('does not trigger app-level undo while typing in a text field (native field undo stays intact)', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const nameInput = screen.getByLabelText(ACTION_LABELS.workflowNameLabel);
    fireEvent.change(nameInput, { target: { value: 'My workflow' } });
    expect(screen.getByTestId('workflow-builder-undo')).not.toBeDisabled();

    fireEvent.keyDown(nameInput, { key: 'z', ctrlKey: true });

    // The app-level history is untouched: Undo is still available (nothing was
    // popped) and the field's own value is unaffected by the app-level handler.
    expect(screen.getByTestId('workflow-builder-undo')).not.toBeDisabled();
    expect((nameInput as HTMLInputElement).value).toBe('My workflow');
  });
});
