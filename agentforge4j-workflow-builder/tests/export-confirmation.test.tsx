// @vitest-environment jsdom
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities, WorkflowDefinition } from '../src/api/types';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

const namedWorkflow: WorkflowDefinition = {
  id: 'greeting-flow',
  name: 'Greeting flow',
  description: '',
  steps: [
    { stepId: 'ask', name: 'Ask', behaviourType: 'INPUT', config: { artifactId: 'a1', transition: 'AUTO' } },
  ],
  artifacts: { a1: { id: 'a1', items: [] } },
};

describe('Export success confirmation', () => {
  it('shows no confirmation before Export is clicked', () => {
    render(<WorkflowBuilder capabilities={{ ...allDisabled, export: true }} />);
    expect(screen.queryByTestId('export-success')).not.toBeInTheDocument();
  });

  it('shows a persisted, visible confirmation with the produced filename after a successful export', async () => {
    const user = userEvent.setup();
    const exportBundle = vi.fn().mockResolvedValue(undefined);
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, export: true }}
        adapters={{ exportBundle }}
      />,
    );

    await user.click(screen.getByTestId('workflow-builder-export'));

    const confirmation = await screen.findByTestId('export-success');
    expect(confirmation).toHaveTextContent('workflow.workflow.zip');

    // The button itself reverts to resting state (the bug being fixed is the absence of any
    // OTHER confirmation, not that the button stops reading "Exporting...").
    await waitFor(() => {
      expect(screen.getByTestId('workflow-builder-export')).toHaveTextContent(ACTION_LABELS.export);
    });

    // Confirmation persists (does not silently vanish on its own).
    expect(screen.getByTestId('export-success')).toBeInTheDocument();
  });

  it('names the file after the workflow id, matching the zip export convention', async () => {
    const user = userEvent.setup();
    const exportBundle = vi.fn().mockResolvedValue(undefined);
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, export: true }}
        adapters={{ exportBundle }}
        initialWorkflow={namedWorkflow}
      />,
    );

    await user.click(screen.getByTestId('workflow-builder-export'));

    const confirmation = await screen.findByTestId('export-success');
    expect(confirmation).toHaveTextContent('greeting-flow.workflow.zip');
  });

  it('can be dismissed', async () => {
    const user = userEvent.setup();
    const exportBundle = vi.fn().mockResolvedValue(undefined);
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, export: true }}
        adapters={{ exportBundle }}
      />,
    );

    await user.click(screen.getByTestId('workflow-builder-export'));
    await screen.findByTestId('export-success');

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.dismissExportSuccess }));
    expect(screen.queryByTestId('export-success')).not.toBeInTheDocument();
  });

  it('clears the previous confirmation and shows the error instead when export fails', async () => {
    const user = userEvent.setup();
    const exportBundle = vi.fn().mockRejectedValue(new Error('disk full'));
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, export: true }}
        adapters={{ exportBundle }}
      />,
    );

    await user.click(screen.getByTestId('workflow-builder-export'));

    expect(await screen.findByRole('alert')).toHaveTextContent('disk full');
    expect(screen.queryByTestId('export-success')).not.toBeInTheDocument();
  });
});
