import type { ValidationResult, WorkflowDefinition } from '../api/types';

export interface BuilderSelection {
  nodeId: string | null;
  edgeId: string | null;
}

export interface ImportMeta {
  source: string | null;
  importedAt: string | null;
}

export interface BuilderState {
  draft: WorkflowDefinition;
  baseline: WorkflowDefinition;
  selection: BuilderSelection;
  validation: ValidationResult;
  importMeta: ImportMeta;
}

export type BuilderAction =
  | { type: 'SET_DRAFT'; draft: WorkflowDefinition }
  | { type: 'SET_BASELINE'; baseline: WorkflowDefinition }
  | { type: 'SELECT_NODE'; nodeId: string | null }
  | { type: 'SELECT_EDGE'; edgeId: string | null }
  | { type: 'SET_VALIDATION'; validation: ValidationResult }
  | { type: 'SET_IMPORT_META'; importMeta: ImportMeta };

export const emptyWorkflow: WorkflowDefinition = { nodes: [], edges: [] };

export const initialValidation: ValidationResult = { valid: true, issues: [] };

export const initialImportMeta: ImportMeta = { source: null, importedAt: null };

export function createInitialState(
  workflow: WorkflowDefinition = emptyWorkflow,
): BuilderState {
  return {
    draft: workflow,
    baseline: workflow,
    selection: { nodeId: null, edgeId: null },
    validation: initialValidation,
    importMeta: initialImportMeta,
  };
}

export function isDirty(state: BuilderState): boolean {
  return JSON.stringify(state.draft) !== JSON.stringify(state.baseline);
}

export function builderReducer(state: BuilderState, action: BuilderAction): BuilderState {
  switch (action.type) {
    case 'SET_DRAFT':
      return { ...state, draft: action.draft };
    case 'SET_BASELINE':
      return { ...state, baseline: action.baseline };
    case 'SELECT_NODE':
      return {
        ...state,
        selection: { ...state.selection, nodeId: action.nodeId, edgeId: null },
      };
    case 'SELECT_EDGE':
      return {
        ...state,
        selection: { ...state.selection, edgeId: action.edgeId, nodeId: null },
      };
    case 'SET_VALIDATION':
      return { ...state, validation: action.validation };
    case 'SET_IMPORT_META':
      return { ...state, importMeta: action.importMeta };
    default:
      return state;
  }
}
