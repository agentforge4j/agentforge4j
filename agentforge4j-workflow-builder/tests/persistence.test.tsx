// @vitest-environment jsdom
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities, BuilderPersistenceAdapter } from '../src/api/types';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';
import type { CanvasModel } from '../src/model/canvasModel';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

const DRAFT_KEY = 'agentforge_builder_draft';
const DEBOUNCE_WAIT = { timeout: 2000 };

function sampleCanvasModel(workflowName: string): CanvasModel {
  return {
    workflowId: 'restored-wf',
    workflowName,
    description: '',
    startNodeId: 'c-ask-1',
    nodes: [
      {
        id: 'c-ask-1',
        backendStepId: 'ask-1',
        kind: 'ASK_USER',
        position: { x: 80, y: 120 },
        data: { name: 'Question', question: 'What?', artifactItems: [] },
      },
    ],
    edges: [],
    artifacts: {},
    blueprints: {},
  } as CanvasModel;
}

describe('workflow-builder draft persistence', () => {
  // Both the built-in adapter and useBuilderMode persist to localStorage, so a value left
  // over from an earlier test (in this file or another) must not leak in.
  beforeEach(() => {
    window.localStorage.clear();
  });

  describe('default localStorage adapter', () => {
    it('persists edits (debounced) and restores them, with a notice, on a fresh mount', async () => {
      const { unmount } = render(<WorkflowBuilder capabilities={allDisabled} />);

      const nameInput = screen.getByLabelText(ACTION_LABELS.workflowNameLabel);
      fireEvent.change(nameInput, { target: { value: 'My Draft Workflow' } });

      await waitFor(() => {
        const raw = window.localStorage.getItem(DRAFT_KEY);
        expect(raw).toBeTruthy();
        expect(JSON.parse(raw as string)).toMatchObject({ workflowName: 'My Draft Workflow' });
      }, DEBOUNCE_WAIT);

      unmount();

      render(<WorkflowBuilder capabilities={allDisabled} />);

      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('My Draft Workflow');
    });

    it('does not show a restore notice when there is nothing meaningful to restore', () => {
      render(<WorkflowBuilder capabilities={allDisabled} />);
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
    });

    it('"Start fresh" clears the notice, the saved draft, and resets the canvas', async () => {
      window.localStorage.setItem(DRAFT_KEY, JSON.stringify(sampleCanvasModel('Restored Workflow')));

      render(<WorkflowBuilder capabilities={allDisabled} />);

      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('Restored Workflow');

      const user = userEvent.setup();
      await user.click(screen.getByTestId('draft-restored-start-fresh'));

      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('"Dismiss" hides the notice without discarding the restored draft', async () => {
      window.localStorage.setItem(DRAFT_KEY, JSON.stringify(sampleCanvasModel('Restored Workflow')));

      render(<WorkflowBuilder capabilities={allDisabled} />);

      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();

      const user = userEvent.setup();
      await user.click(screen.getByTestId('draft-restored-dismiss'));

      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('Restored Workflow');
    });
  });

  describe('host-supplied persistence adapter', () => {
    function fakeAdapter(initial: CanvasModel | null): BuilderPersistenceAdapter & {
      load: ReturnType<typeof vi.fn>;
      save: ReturnType<typeof vi.fn>;
      clear: ReturnType<typeof vi.fn>;
    } {
      return {
        load: vi.fn().mockResolvedValue(initial),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };
    }

    it('uses the host load/save instead of localStorage', async () => {
      const adapter = fakeAdapter(sampleCanvasModel('Host Restored'));

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);

      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));
      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('Host Restored');
      // The default localStorage adapter must never be touched when a host adapter is supplied.
      expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();

      const nameInput = screen.getByLabelText(ACTION_LABELS.workflowNameLabel);
      fireEvent.change(nameInput, { target: { value: 'Host Restored Edited' } });

      await waitFor(() => {
        expect(adapter.save).toHaveBeenCalled();
        const lastCall = adapter.save.mock.calls[adapter.save.mock.calls.length - 1]?.[0] as CanvasModel;
        expect(lastCall.workflowName).toBe('Host Restored Edited');
      }, DEBOUNCE_WAIT);

      expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('uses the host clear() instead of localStorage.removeItem when starting fresh', async () => {
      const adapter = fakeAdapter(sampleCanvasModel('Host Restored'));

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);

      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();

      const user = userEvent.setup();
      await user.click(screen.getByTestId('draft-restored-start-fresh'));

      expect(adapter.clear).toHaveBeenCalledTimes(1);
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
    });

    it('never calls load()/save() when a host initialWorkflow already seeds real content', async () => {
      const adapter = fakeAdapter(sampleCanvasModel('Should Not Restore'));

      render(
        <WorkflowBuilder
          capabilities={allDisabled}
          persistence={adapter}
          initialWorkflow={{
            id: 'seeded-wf',
            name: 'Seeded',
            description: '',
            steps: [
              {
                stepId: 'ask',
                name: 'Ask',
                behaviourType: 'INPUT',
                config: { artifactId: 'a1', transition: 'AUTO' },
              },
            ],
            artifacts: {},
          }}
        />,
      );

      // Give any (incorrect) async load a chance to resolve before asserting it never fired.
      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(adapter.load).not.toHaveBeenCalled();
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('Seeded');
    });
  });
});
