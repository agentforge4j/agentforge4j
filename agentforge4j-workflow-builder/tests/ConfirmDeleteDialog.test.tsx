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

  it('calls onCancel on Escape dispatched from inside the dialog (as a real keypress would target it — focus is trapped there)', () => {
    const onCancel = vi.fn();
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={onCancel} />);
    fireEvent.keyDown(screen.getByRole('button', { name: ACTION_LABELS.confirmDeleteCancel }), { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('ignores Escape dispatched outside the dialog (a host page or a second instance must not be cancelled by this one)', () => {
    const onCancel = vi.fn();
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={onCancel} />);
    fireEvent.keyDown(document.body, { key: 'Escape' });
    expect(onCancel).not.toHaveBeenCalled();
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('associates the body message as the alertdialog\'s accessible description', () => {
    render(<ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={() => {}} />);
    const dialog = screen.getByRole('alertdialog');
    const describedById = dialog.getAttribute('aria-describedby');
    expect(describedById).toBeTruthy();
    expect(document.getElementById(describedById!)).toHaveTextContent(ACTION_LABELS.confirmDeleteBody(1));
  });

  it('does not restore focus to the (still-connected) opener on a CONFIRM close — the caller owns focus once the deletion commits', () => {
    const opener = document.createElement('button');
    document.body.appendChild(opener);
    opener.focus();

    const { rerender } = render(
      <ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={() => {}} />,
    );
    // Real confirm click: outcome must be recorded as 'confirm' before the dialog closes.
    fireEvent.click(screen.getByRole('button', { name: ACTION_LABELS.confirmDeleteConfirm }));

    // Opener is still connected at this point (mirrors the real inspector "Delete step"
    // button, which only unmounts once the deferred deletion itself later commits).
    expect(opener.isConnected).toBe(true);

    rerender(<ConfirmDeleteDialog count={0} onConfirm={() => {}} onCancel={() => {}} />);

    // Must NOT have been refocused to the (still-connected) opener.
    expect(document.activeElement).not.toBe(opener);
  });

  it('falls back to fallbackFocusRef on close when the opener is no longer connected', () => {
    const opener = document.createElement('button');
    document.body.appendChild(opener);
    opener.focus();

    const fallback = document.createElement('div');
    fallback.tabIndex = -1;
    document.body.appendChild(fallback);
    const fallbackFocusRef = { current: fallback };

    // Mount while the opener is still focused/connected — its identity is captured as the
    // usual restoration target, exactly as it would be for a real "Delete step" click.
    const { rerender } = render(
      <ConfirmDeleteDialog count={1} onConfirm={() => {}} onCancel={() => {}} fallbackFocusRef={fallbackFocusRef} />,
    );

    // The opener unmounts while the dialog is still open — the real confirm path does this:
    // the inspector (and its "Delete step" button) unmount once the deletion goes through.
    opener.remove();

    rerender(
      <ConfirmDeleteDialog count={0} onConfirm={() => {}} onCancel={() => {}} fallbackFocusRef={fallbackFocusRef} />,
    );

    expect(document.activeElement).toBe(fallback);
  });
});
