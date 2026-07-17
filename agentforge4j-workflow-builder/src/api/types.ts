// SPDX-License-Identifier: Apache-2.0

export type BehaviourType =
  | 'INPUT'
  | 'AGENT'
  | 'SPAR'
  | 'BRANCH'
  | 'WORKFLOW_BEHAVIOUR'
  | 'RESOURCE'
  | 'FAIL'
  | 'RETRY_PREVIOUS';

export type StepTransition = 'AUTO' | 'HUMAN_REVIEW' | 'HUMAN_APPROVAL';
export type RetryMode = 'SINGLE_STEP' | 'FROM_STEP';
export type ArtifactItemType =
  | 'TEXT'
  | 'TEXT_AREA'
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'BOOLEAN'
  | 'NUMBER'
  | 'DATE';

export type ContextMapping = {
  inputKeys: string[];
  outputKeys: string[];
};

export type InputConfig = { artifactId: string; transition: StepTransition };
export type AgentConfig = { agentRef: string; transition: StepTransition; maxRetries?: number };
export type SparConfig = {
  agentRef: string;
  challengerAgentId: string;
  maxRounds: number;
  resolutionPrompt: string;
  transition: StepTransition;
};
export type BranchConfig = {
  contextKey: string;
  branches: Record<string, string>;
  defaultBranch: string;
};
export type WorkflowBehaviourConfig = { workflowRef: string; transition: StepTransition };
export type ResourceConfig = { resourcePath: string; contextKey: string; transition: StepTransition };
export type FailConfig = { reason: string };
export type RetryPreviousConfig = {
  retryStepId: string;
  retryMode: RetryMode;
  maxAttempts: number;
  fallback: string;
};

export type StepConfig =
  | InputConfig
  | AgentConfig
  | SparConfig
  | BranchConfig
  | WorkflowBehaviourConfig
  | ResourceConfig
  | FailConfig
  | RetryPreviousConfig;

export type EditableArtifactItem = {
  id: string;
  type: ArtifactItemType;
  label: string;
  required: boolean;
  options?: string[];
};

export type EditableArtifact = {
  id: string;
  items: EditableArtifactItem[];
};

export type EditableStep = {
  stepId: string;
  name: string;
  behaviourType: BehaviourType;
  config: StepConfig;
  stepPrompt?: string;
  contextMapping?: ContextMapping;
};

export type BlueprintJsonObject = Record<string, unknown>;

export type TopLevelScheduleEntry =
  | { kind: 'STEP'; stepIndex: number }
  | { kind: 'BLUEPRINT_REF'; blueprintId: string };

/** Canonical workflow document shape used by the builder draft and host adapters. */
export interface WorkflowDefinition {
  id: string;
  name: string;
  description: string;
  steps: EditableStep[];
  artifacts: Record<string, EditableArtifact>;
  blueprintBodies?: Record<string, BlueprintJsonObject>;
  topLevelSchedule?: TopLevelScheduleEntry[];
}

export type ExportFormat = 'json' | 'zip';

export interface ValidationIssue {
  path: string;
  message: string;
  severity: 'error' | 'warning';
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface AgentRef {
  id: string;
  name: string;
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
  /** Optional CSS custom properties applied on the builder root element. */
  variables?: Record<string, string>;
}

/**
 * Optional outcome descriptor an {@link BuilderAdapters.exportBundle} implementation may
 * resolve. When `filename` is present, the builder's on-page export confirmation names that
 * exact file; resolving `void` shows a generic confirmation instead — the builder never
 * fabricates a filename the adapter did not actually produce.
 */
export interface ExportOutcome {
  /** The filename the export actually produced, for the user-facing confirmation. */
  filename?: string;
}

export interface BuilderAdapters {
  validateWorkflow?: (draft: WorkflowDefinition) => ValidationResult | Promise<ValidationResult>;
  importBundle?: () => Promise<WorkflowDefinition>;
  /**
   * Export the draft. May resolve an {@link ExportOutcome} naming the produced file (the
   * built-in adapter does); a plain `void` resolution keeps the confirmation generic.
   * Existing `Promise<void>` implementations remain valid.
   */
  exportBundle?: (draft: WorkflowDefinition, format: ExportFormat) => Promise<void | ExportOutcome>;
}

export interface BuilderActions {
  save?: (draft: WorkflowDefinition) => Promise<void>;
  run?: (draft: WorkflowDefinition) => Promise<void>;
  publish?: (draft: WorkflowDefinition) => Promise<void>;
}

/**
 * Editing posture for the builder. `readOnly` blocks every workflow-graph and
 * definition mutation regardless of `capabilities` (a hard override for mutating
 * actions); non-mutating interaction — pan/zoom/select/inspect/validate/export —
 * stays available. Defaults to `editable`, which preserves prior behaviour and
 * `capabilities` semantics exactly.
 */
export type WorkflowBuilderMode = 'editable' | 'readOnly';

export interface WorkflowBuilderProps {
  capabilities: BuilderCapabilities;
  adapters?: BuilderAdapters;
  actions?: BuilderActions;
  theme?: BuilderTheme;
  initialWorkflow?: WorkflowDefinition;
  /** Host-supplied agent catalog for inspector pickers (text fallback when omitted). */
  agentCatalog?: AgentRef[];
  /** Editing posture; defaults to `editable`. See {@link WorkflowBuilderMode}. */
  mode?: WorkflowBuilderMode;
}

export function emptyWorkflow(): WorkflowDefinition {
  return {
    id: '',
    name: '',
    description: '',
    steps: [],
    artifacts: {},
  };
}
