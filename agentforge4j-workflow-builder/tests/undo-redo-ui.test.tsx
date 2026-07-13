// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities } from '../src/api/types';
import { ACTION_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';

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

    fireEvent.keyDown(window, { key: 'z', ctrlKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);

    fireEvent.keyDown(window, { key: 'Z', ctrlKey: true, shiftKey: true });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);
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
