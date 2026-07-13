// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDeleteDialog } from '../src/dialogs/ConfirmDeleteDialog';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';

describe('ConfirmDeleteDialog', () => {
  it('renders nothing when count is 0', () => {
    const { container } = render(
      <ConfirmDeleteDialog count={0} onConfirm={() => {}} onCancel={() => {}} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the named-step title for a single step with a known label', () => {
    render(<ConfirmDeleteDialog count={1} singleStepLabel="Ask the customer" onConfirm={() => {}} onCancel={() => {}} />);
    expect(screen.getByRole('alertdialog', { name: 'Delete "Ask the customer"?' })).toBeInTheDocument();
  });

  it('falls back to a generic single-step title when no label is known', () => {
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={() => {}} />);
    expect(screen.getByRole('alertdialog', { name: ACTION_LABELS.confirmDeleteTitle(1) })).toBeInTheDocument();
  });

  it('shows a count-based title for a multi-step deletion', () => {
    render(<ConfirmDeleteDialog count={3} onConfirm={() => {}} onCancel={() => {}} />);
    expect(screen.getByRole('alertdialog', { name: ACTION_LABELS.confirmDeleteTitle(3) })).toBeInTheDocument();
    expect(screen.getByText(ACTION_LABELS.confirmDeleteBody(3))).toBeInTheDocument();
  });

  it('calls onConfirm when the Delete button is clicked', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<ConfirmDeleteDialog count={1} onConfirm={onConfirm} onCancel={() => {}} />);
    await user.click(screen.getByRole('button', { name: ACTION_LABELS.confirmDeleteConfirm }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel when the Cancel button is clicked', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={onCancel} />);
    await user.click(screen.getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel when the backdrop is clicked', () => {
    const onCancel = vi.fn();
    const { container } = render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={onCancel} />);
    fireEvent.click(container.querySelector('.wf-confirm-dialog__backdrop')!);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel on Escape', () => {
    const onCancel = vi.fn();
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={onCancel} />);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
