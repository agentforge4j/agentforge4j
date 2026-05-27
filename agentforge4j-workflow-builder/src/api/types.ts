/**
 * Skeletal workflow document shape. Replaced with the real schema in Phase 3.
 */
export interface WorkflowDefinition {
  id?: string;
  name?: string;
  version?: string;
  nodes?: unknown[];
  edges?: unknown[];
  [key: string]: unknown;
}

export type ExportFormat = 'json';

export interface ValidationIssue {
  path: string;
  message: string;
  severity: 'error' | 'warning';
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface BuilderCapabilities {
  import: boolean;
  export: boolean;
  save: boolean;
  run: boolean;
  publish: boolean;
  aiAssist: boolean;
}

export interface BuilderTheme {
  /** Optional root class; layout uses semantic CSS variables only. */
  className?: string;
}

export interface BuilderAdapters {
  validateWorkflow?: (draft: WorkflowDefinition) => ValidationResult | Promise<ValidationResult>;
  importBundle?: () => Promise<WorkflowDefinition>;
  exportBundle?: (draft: WorkflowDefinition, format: ExportFormat) => Promise<void>;
}

export interface BuilderActions {
  save?: (draft: WorkflowDefinition) => Promise<void>;
  run?: (draft: WorkflowDefinition) => Promise<void>;
  publish?: (draft: WorkflowDefinition) => Promise<void>;
}

export interface WorkflowBuilderProps {
  capabilities: BuilderCapabilities;
  adapters?: BuilderAdapters;
  actions?: BuilderActions;
  theme?: BuilderTheme;
  initialWorkflow?: WorkflowDefinition;
}
