import { describe, expect, it } from 'vitest';
import {
  builderReducer,
  createInitialState,
  emptyWorkflow,
  isDirty,
  type BuilderState,
} from '../src/state/reducer';

describe('builderReducer', () => {
  const base = createInitialState({ ...emptyWorkflow(), id: 'wf-1', name: 'Test' });

  it('SET_DRAFT updates draft and derived dirty when baseline differs', () => {
    const next = builderReducer(base, {
      type: 'SET_DRAFT',
      draft: { ...base.draft, name: 'Changed' },
    });
    expect(next.draft.name).toBe('Changed');
    expect(isDirty(next)).toBe(true);
  });

  it('SET_BASELINE clears dirty when draft matches', () => {
    const edited: BuilderState = {
      ...base,
      draft: { ...base.draft, name: 'Changed' },
    };
    expect(isDirty(edited)).toBe(true);
    const synced = builderReducer(edited, {
      type: 'SET_BASELINE',
      baseline: edited.draft,
    });
    expect(isDirty(synced)).toBe(false);
  });

  it('SELECT_NODE and SELECT_EDGE update selection', () => {
    const withNode = builderReducer(base, { type: 'SELECT_NODE', nodeId: 'n-1' });
    expect(withNode.selection.nodeId).toBe('n-1');
    expect(withNode.selection.edgeId).toBeNull();

    const withEdge = builderReducer(withNode, { type: 'SELECT_EDGE', edgeId: 'e-1' });
    expect(withEdge.selection.edgeId).toBe('e-1');
    expect(withEdge.selection.nodeId).toBeNull();
  });

  it('SET_VALIDATION and SET_IMPORT_META update respective slices', () => {
    const validated = builderReducer(base, {
      type: 'SET_VALIDATION',
      validation: { valid: false, issues: [{ path: 'x', message: 'bad', severity: 'error' }] },
    });
    expect(validated.validation.valid).toBe(false);

    const imported = builderReducer(validated, {
      type: 'SET_IMPORT_META',
      importMeta: { source: 'file', importedAt: '2026-05-27T00:00:00.000Z' },
    });
    expect(imported.importMeta.source).toBe('file');
  });

  it('does not store dirty on state', () => {
    expect('dirty' in base).toBe(false);
  });
});
