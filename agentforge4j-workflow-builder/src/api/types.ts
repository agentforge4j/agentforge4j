// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel } from '../model/canvasModel';

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

/**
 * Draft-recovery persistence for the canvas model — independent of {@link BuilderActions.save},
 * which gates a host backend-persistence action behind `capabilities.save`. This mechanism is
 * always active while the builder is editable, regardless of that capability's value.
 *
 * When a host does not supply a `persistence` prop, the builder falls back to a built-in
 * `localStorage`-backed implementation. The package itself never makes a network call for
 * this either way; the standalone builder must never require a server.
 */
export interface BuilderPersistenceAdapter {
  /**
   * Load a previously saved draft, if any. Called once on mount. Return (or resolve to)
   * `null` when there is nothing to restore.
   */
  load: () => Promise<CanvasModel | null> | CanvasModel | null;
  /** Persist the current canvas model. Called on a debounce after each meaningful edit. */
  save: (model: CanvasModel) => Promise<void> | void;
  /** Clear any saved draft. Invoked by the built-in "Start fresh" action. */
  clear?: () => Promise<void> | void;
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
  /**
   * Draft-recovery persistence override. Defaults to a built-in `localStorage`-backed
   * implementation when omitted. See {@link BuilderPersistenceAdapter}. Pass a referentially
   * stable object (e.g. via `useMemo`/module scope) — a new object identity on every render
   * resets the internal save debounce.
   */
  persistence?: BuilderPersistenceAdapter;
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
