// @vitest-environment jsdom
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities, BuilderPersistenceAdapter } from '../src/api/types';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';
import type { CanvasModel } from '../src/model/canvasModel';
import { DRAFT_STORAGE_VERSION } from '../src/persistence/localStoragePersistence';

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

/** The exact envelope the built-in adapter persists — see localStoragePersistence.ts. */
function storedDraft(model: CanvasModel): string {
  return JSON.stringify({ version: DRAFT_STORAGE_VERSION, model });
}

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
        expect(JSON.parse(raw as string)).toMatchObject({
          version: DRAFT_STORAGE_VERSION,
          model: { workflowName: 'My Draft Workflow' },
        });
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
      window.localStorage.setItem(DRAFT_KEY, storedDraft(sampleCanvasModel('Restored Workflow')));

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
      window.localStorage.setItem(DRAFT_KEY, storedDraft(sampleCanvasModel('Restored Workflow')));

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

    it('never restores over a metadata-only initialWorkflow (id/name seeded, zero steps)', async () => {
      const adapter = fakeAdapter(sampleCanvasModel('Should Not Restore'));

      render(
        <WorkflowBuilder
          capabilities={allDisabled}
          persistence={adapter}
          initialWorkflow={{ id: 'named-empty', name: 'Named But Empty', description: '', steps: [], artifacts: {} }}
        />,
      );

      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(adapter.load).not.toHaveBeenCalled();
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      // Note: the mount-time canvas derivation only maps `initialWorkflow` with ≥1 step into
      // the canvas (a zero-step seed starts from the blank starter — pre-existing behavior),
      // so the name field is empty here. What this test locks in is the restore gate: the
      // stored draft ("Should Not Restore") must never appear either.
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
    });
  });

  describe('fail-closed restore (mis-shaped or stale drafts)', () => {
    it.each([
      ['legacy unversioned draft (raw model, pre-envelope format)', JSON.stringify(sampleCanvasModel('Old Format'))],
      ['plausible-envelope but mis-shaped model', JSON.stringify({ version: DRAFT_STORAGE_VERSION, model: { nodes: [] } })],
      ['unknown future version', JSON.stringify({ version: 9999, model: sampleCanvasModel('From The Future') })],
      ['truncated/corrupt JSON', '{"version":1,"model":{"nodes":['],
    ])('discards %s instead of restoring (and clears it so it cannot crash the next mount)', (_label, raw) => {
      window.localStorage.setItem(DRAFT_KEY, raw);

      render(<WorkflowBuilder capabilities={allDisabled} />);

      // Fresh editor, no banner, no crash — and the bad draft is gone.
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('skips (without clearing) a host adapter draft that fails the structural gate', async () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const adapter = {
        load: vi.fn().mockResolvedValue({ nodes: [] } as unknown as CanvasModel),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);

      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      // The host's storage is theirs — the builder must not destroy it, only ignore it.
      expect(adapter.clear).not.toHaveBeenCalled();
      await waitFor(() => expect(warnSpy).toHaveBeenCalled());
      warnSpy.mockRestore();
    });
  });

  describe('read-only mode', () => {
    it('never calls load() or save() in read-only mode', async () => {
      const adapter = {
        load: vi.fn().mockResolvedValue(sampleCanvasModel('Should Not Restore')),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} mode="readOnly" />);

      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(adapter.load).not.toHaveBeenCalled();
      expect(adapter.save).not.toHaveBeenCalled();
    });
  });

  describe('pending-save flush on unmount (SPA navigation)', () => {
    it('flushes an unflushed edit to the adapter when the builder unmounts before the debounce fires', async () => {
      const adapter = {
        load: vi.fn().mockResolvedValue(null),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };
      const { unmount } = render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));

      fireEvent.change(screen.getByLabelText(ACTION_LABELS.workflowNameLabel), {
        target: { value: 'Typed Right Before Navigating' },
      });

      // Unmount immediately — well inside the debounce window — as an SPA route change would.
      unmount();

      expect(adapter.save).toHaveBeenCalled();
      const lastCall = adapter.save.mock.calls[adapter.save.mock.calls.length - 1]?.[0] as CanvasModel;
      expect(lastCall.workflowName).toBe('Typed Right Before Navigating');
    });

    it('does not save on unmount when nothing is pending', async () => {
      const adapter = {
        load: vi.fn().mockResolvedValue(null),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };
      const { unmount } = render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));

      unmount();

      expect(adapter.save).not.toHaveBeenCalled();
    });
  });

  describe('slow async host load racing an early edit', () => {
    it('never replaces edits the user made before load() resolved', async () => {
      let resolveLoad: (value: CanvasModel | null) => void = () => {};
      const adapter = {
        load: vi.fn().mockImplementation(
          () =>
            new Promise<CanvasModel | null>((resolve) => {
              resolveLoad = resolve;
            }),
        ),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));

      // The user starts working while the backend is still loading.
      fireEvent.change(screen.getByLabelText(ACTION_LABELS.workflowNameLabel), {
        target: { value: 'Fresh Work In Progress' },
      });

      resolveLoad(sampleCanvasModel('Stale Backend Draft'));
      await new Promise((resolve) => setTimeout(resolve, 50));

      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('Fresh Work In Progress');
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
    });
  });

  describe('failing host writes', () => {
    it('logs a warning (no unhandled rejection) when the host save() rejects', async () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const adapter = {
        load: vi.fn().mockResolvedValue(null),
        save: vi.fn().mockRejectedValue(new Error('backend down')),
        clear: vi.fn().mockResolvedValue(undefined),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));

      fireEvent.change(screen.getByLabelText(ACTION_LABELS.workflowNameLabel), {
        target: { value: 'Will Fail To Save' },
      });

      await waitFor(() => expect(adapter.save).toHaveBeenCalled(), DEBOUNCE_WAIT);
      await waitFor(() => {
        expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('Failed to save the draft'), expect.any(Error));
      });
      warnSpy.mockRestore();
    });
  });
});
