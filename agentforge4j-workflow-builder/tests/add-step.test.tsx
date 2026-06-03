// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities } from '../src/api/types';
import { NODE_LABELS } from '../src/copy/workflow-terminology';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('add step', () => {
  // jsdom has no ResizeObserver; node visibility:visible is verified in-browser (see canvas.test).
  it('keeps a palette-added step in the draft', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);

    expect(screen.getAllByText(NODE_LABELS.ASK_USER).length).toBeGreaterThan(0);

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });

    expect(screen.getByLabelText(/Agent/i)).toBeInTheDocument();
  });
});
