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
    /** Otherwise-valid envelope; only `nodes` is swapped in per case below. */
    function draftWithNodes(workflowName: string, nodes: unknown[]): string {
      return JSON.stringify({
        version: DRAFT_STORAGE_VERSION,
        model: {
          workflowId: 'wf',
          workflowName,
          description: '',
          startNodeId: 'n1',
          nodes,
          edges: [],
          artifacts: {},
          blueprints: {},
        },
      });
    }

    it.each([
      ['legacy unversioned draft (raw model, pre-envelope format)', JSON.stringify(sampleCanvasModel('Old Format'))],
      ['plausible-envelope but mis-shaped model', JSON.stringify({ version: DRAFT_STORAGE_VERSION, model: { nodes: [] } })],
      ['unknown future version', JSON.stringify({ version: 9999, model: sampleCanvasModel('From The Future') })],
      ['truncated/corrupt JSON', '{"version":1,"model":{"nodes":['],
      [
        'a node with an unrecognized kind (e.g. from a newer builder version)',
        draftWithNodes('Unknown Kind', [{ id: 'n1', kind: 'FUTURE_KIND', position: { x: 0, y: 0 }, data: {} }]),
      ],
      [
        'a DECISION node missing its cases array (would crash DecisionNode/canvasNodeToStep)',
        draftWithNodes('Decision No Cases', [
          { id: 'n1', kind: 'DECISION', position: { x: 0, y: 0 }, data: { name: 'D', contextKey: '', defaultTargetNodeId: '' } },
        ]),
      ],
      [
        'an ASK_USER node missing its artifactItems array (would crash StepConfigPanel)',
        draftWithNodes('Ask No Items', [{ id: 'n1', kind: 'ASK_USER', position: { x: 0, y: 0 }, data: { name: '', question: '' } }]),
      ],
      [
        'an AI_STEP node with empty data (missing agentRef etc. — would crash validateWorkflowEditor)',
        draftWithNodes('Ai Empty Data', [{ id: 'n1', kind: 'AI_STEP', position: { x: 0, y: 0 }, data: {} }]),
      ],
      [
        'a DECISION case entry missing its value/targetNodeId strings (would crash canvasToWorkflow)',
        draftWithNodes('Decision Bad Case', [
          { id: 'n1', kind: 'DECISION', position: { x: 0, y: 0 }, data: { name: 'D', contextKey: '', defaultTargetNodeId: '', cases: [{}] } },
        ]),
      ],
      [
        'a SAVE_RESULT node missing resultName (would crash canvasToWorkflow)',
        draftWithNodes('Save No Result', [{ id: 'n1', kind: 'SAVE_RESULT', position: { x: 0, y: 0 }, data: { name: 'S' } }]),
      ],
      [
        'an artifact definition with no items array (would crash validateWorkflowEditor)',
        JSON.stringify({
          version: DRAFT_STORAGE_VERSION,
          model: { ...sampleCanvasModel('Bad Artifacts'), artifacts: { a1: {} } },
        }),
      ],
    ])('discards %s instead of restoring (and clears it so it cannot crash the next mount)', async (_label, raw) => {
      window.localStorage.setItem(DRAFT_KEY, raw);

      render(<WorkflowBuilder capabilities={allDisabled} />);

      // Fresh editor, no banner, no crash — and the bad draft is gone. The load-on-mount
      // path always resolves through a promise chain (deferred so a synchronously-throwing
      // host `load` can't escape it — see useModelPersistence.ts), so the clear is awaited
      // rather than asserted synchronously right after render.
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      await waitFor(() => {
        expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
      });
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

    it('resets the canvas and logs a warning — rather than throwing — when a JS (non-TypeScript) adapter omits the required clear()', async () => {
      // BuilderPersistenceAdapter.clear is required, so TypeScript would reject this object
      // literal; a plain-JS consumer bypassing the type system could still hand the builder
      // an adapter shaped like this, so the runtime path must stay defensive rather than
      // crashing "Start fresh" outright.
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const adapter = {
        load: vi.fn().mockResolvedValue(sampleCanvasModel('Restored Workflow')),
        save: vi.fn().mockResolvedValue(undefined),
      } as unknown as BuilderPersistenceAdapter;

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);

      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();

      const user = userEvent.setup();
      await user.click(screen.getByTestId('draft-restored-start-fresh'));

      // The local canvas still resets — the missing clear() must not crash the action.
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      expect(screen.getByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      await waitFor(() => {
        expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('Failed to clear the saved draft'), expect.any(Error));
      });
      warnSpy.mockRestore();
    });
  });

  describe('synchronously throwing host load()', () => {
    it('skips the restore instead of crashing the tree when a host adapter throws synchronously from load()', async () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const adapter = {
        load: vi.fn(() => {
          throw new Error('boom');
        }),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);

      // The editor must still render and be usable — a sync throw from load() would
      // previously escape the mount effect uncaught and tear down the whole tree.
      expect(await screen.findByLabelText(ACTION_LABELS.workflowNameLabel)).toHaveValue('');
      expect(screen.queryByTestId('draft-restored-banner')).not.toBeInTheDocument();
      await waitFor(() => {
        expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('Failed to load a saved draft'), expect.any(Error));
      });
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

      // The flush is dispatched through the ordered write queue (one microtask), so await it.
      await waitFor(() => expect(adapter.save).toHaveBeenCalled());
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

  describe('beforeunload safety net', () => {
    it('blocks unload only while an edit is still unflushed', async () => {
      const adapter = {
        load: vi.fn().mockResolvedValue(null),
        save: vi.fn().mockResolvedValue(undefined),
        clear: vi.fn().mockResolvedValue(undefined),
      };
      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      await waitFor(() => expect(adapter.load).toHaveBeenCalledTimes(1));

      // Nothing pending yet: unload is not blocked.
      const beforeEdit = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(beforeEdit);
      expect(beforeEdit.defaultPrevented).toBe(false);

      fireEvent.change(screen.getByLabelText(ACTION_LABELS.workflowNameLabel), {
        target: { value: 'Unflushed Edit' },
      });

      // Inside the debounce window the edit is unprotected — unload must warn.
      const whilePending = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(whilePending);
      expect(whilePending.defaultPrevented).toBe(true);

      // Once the debounced save settles, the warning disarms again.
      await waitFor(() => expect(adapter.save).toHaveBeenCalled(), DEBOUNCE_WAIT);
      await waitFor(() => {
        const afterFlush = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(afterFlush);
        expect(afterFlush.defaultPrevented).toBe(false);
      });
    });
  });

  describe('in-flight save racing "Start fresh"', () => {
    it('runs clear() strictly after a still-in-flight save so the discarded draft cannot resurrect', async () => {
      const events: string[] = [];
      let resolveSave: () => void = () => {};
      const adapter = {
        load: vi.fn().mockResolvedValue(sampleCanvasModel('Restored Workflow')),
        save: vi.fn().mockImplementation(
          () =>
            new Promise<void>((resolve) => {
              resolveSave = () => {
                events.push('save-settled');
                resolve();
              };
            }),
        ),
        clear: vi.fn().mockImplementation(() => {
          events.push('clear');
        }),
      };

      render(<WorkflowBuilder capabilities={allDisabled} persistence={adapter} />);
      expect(await screen.findByTestId('draft-restored-banner')).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText(ACTION_LABELS.workflowNameLabel), {
        target: { value: 'Edited Before Discard' },
      });
      await waitFor(() => expect(adapter.save).toHaveBeenCalledTimes(1), DEBOUNCE_WAIT);

      const user = userEvent.setup();
      await user.click(screen.getByTestId('draft-restored-start-fresh'));

      // The save dispatched before "Start fresh" is still in flight: the clear must wait
      // for it — issuing it now would let the slow save re-persist the discarded draft.
      expect(adapter.clear).not.toHaveBeenCalled();

      resolveSave();
      await waitFor(() => expect(adapter.clear).toHaveBeenCalledTimes(1));
      expect(events).toEqual(['save-settled', 'clear']);
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
