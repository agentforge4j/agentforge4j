export { WorkflowBuilder } from './api/WorkflowBuilder';
export { WorkflowBuilder as default } from './api/WorkflowBuilder';
export type {
  AgentRef,
  BuilderActions,
  BuilderAdapters,
  BuilderCapabilities,
  BuilderTheme,
  ExportFormat,
  ValidationIssue,
  ValidationResult,
  WorkflowBuilderProps,
  WorkflowDefinition,
} from './api/types';
export { useBuilderState } from './state/useBuilderState';
export type { UseBuilderStateResult } from './state/useBuilderState';
export { builderReducer } from './state/reducer';
export type { BuilderAction, BuilderState } from './state/reducer';
export { validateWorkflow } from './validation/validateWorkflow';
export { parseWorkflowJson, serializeWorkflowJson, WorkflowParseError } from './io/core';

// Model — exported for host-app consumers that render node metadata
export type { NodeKind } from './model/nodeKinds';
export { NODE_KIND_META } from './model/nodeKinds';
export type { CanvasModel } from './model/canvasModel';
