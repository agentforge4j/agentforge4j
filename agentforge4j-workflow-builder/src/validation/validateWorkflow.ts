// SPDX-License-Identifier: Apache-2.0

import type {
  BranchConfig,
  EditableArtifactItem,
  RetryPreviousConfig,
  ValidationIssue,
  ValidationResult,
  WorkflowDefinition,
} from '../api/types';

const ID_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

type EditorValidation = {
  workflow: Partial<Record<'id' | 'name' | 'description', string>>;
  steps: Record<number, Partial<Record<string, string>>>;
  artifacts: Record<string, Partial<Record<string, string>>>;
  global: string[];
};

function hasOptions(item: EditableArtifactItem): boolean {
  if (item.type !== 'SINGLE_CHOICE' && item.type !== 'MULTI_CHOICE') {
    return true;
  }
  return Boolean(item.options?.some((o) => o.trim().length > 0));
}

function collectNestedOnlyStepIds(workflow: WorkflowDefinition): Set<string> {
  const nested = new Set<string>();
  for (const step of workflow.steps) {
    if (step.behaviourType === 'BRANCH') {
      const c = step.config as BranchConfig;
      Object.values(c.branches).forEach((id) => {
        if (id.trim()) {
          nested.add(id.trim());
        }
      });
      if (c.defaultBranch.trim()) {
        nested.add(c.defaultBranch.trim());
      }
    }
    if (step.behaviourType === 'RETRY_PREVIOUS') {
      const c = step.config as RetryPreviousConfig;
      if (c.fallback.trim()) {
        nested.add(c.fallback.trim());
      }
    }
  }
  return nested;
}

function validateEditableWorkflow(workflow: WorkflowDefinition): EditorValidation {
  const errors: EditorValidation = {
    workflow: {},
    steps: {},
    artifacts: {},
    global: [],
  };

  if (!workflow.id.trim()) {
    errors.workflow.id = 'Workflow id is required.';
  } else if (!ID_PATTERN.test(workflow.id.trim())) {
    errors.workflow.id = 'Workflow id must use lowercase letters, numbers, and dashes.';
  }
  if (!workflow.name.trim()) {
    errors.workflow.name = 'Workflow name is required.';
  }
  if (workflow.steps.length === 0) {
    errors.global.push('At least one step is required.');
  }

  if (workflow.topLevelSchedule && workflow.topLevelSchedule.length > 0) {
    const nestedOnly = collectNestedOnlyStepIds(workflow);
    workflow.topLevelSchedule.forEach((entry, scheduleIndex) => {
      if (entry.kind === 'STEP') {
        if (!Number.isInteger(entry.stepIndex) || entry.stepIndex < 0 || entry.stepIndex >= workflow.steps.length) {
          errors.global.push(`Top-level schedule entry ${scheduleIndex}: invalid step index ${entry.stepIndex}.`);
        } else {
          const st = workflow.steps[entry.stepIndex];
          if (st && nestedOnly.has(st.stepId.trim())) {
            errors.global.push(
              `Top-level schedule entry ${scheduleIndex}: step "${st.stepId}" is nested-only and cannot appear in the main schedule.`,
            );
          }
        }
      } else if (entry.kind === 'BLUEPRINT_REF') {
        if (!entry.blueprintId?.trim()) {
          errors.global.push(`Top-level schedule entry ${scheduleIndex}: blueprint id is required.`);
        }
      }
    });
  }

  const seenStepIds = new Set<string>();
  workflow.steps.forEach((step, index) => {
    const stepErrors: Partial<Record<string, string>> = {};
    if (!step.stepId.trim()) {
      stepErrors.stepId = 'Step id is required.';
    } else if (!ID_PATTERN.test(step.stepId.trim())) {
      stepErrors.stepId = 'Step id must use lowercase letters, numbers, and dashes.';
    } else if (seenStepIds.has(step.stepId.trim())) {
      stepErrors.stepId = 'Step id must be unique.';
    } else {
      seenStepIds.add(step.stepId.trim());
    }

    if (!step.name.trim()) {
      stepErrors.name = 'Step name is required.';
    }

    if (step.behaviourType === 'INPUT') {
      const config = step.config as { artifactId: string };
      if (!config.artifactId.trim()) {
        stepErrors.artifactId = 'Artifact id is required for INPUT steps.';
      } else if (!workflow.artifacts[config.artifactId]) {
        stepErrors.artifactId = `Artifact "${config.artifactId}" does not exist.`;
      }
    }

    if (step.behaviourType === 'AGENT') {
      const config = step.config as { agentRef: string };
      if (!config.agentRef.trim()) {
        stepErrors.agentRef = 'agentRef is required.';
      }
    }

    if (step.behaviourType === 'SPAR') {
      const config = step.config as {
        agentRef: string;
        challengerAgentId: string;
        maxRounds: number;
        resolutionPrompt: string;
      };
      if (!config.agentRef.trim()) {
        stepErrors.agentRef = 'agentRef is required.';
      }
      if (!config.challengerAgentId.trim()) {
        stepErrors.challengerAgentId = 'challengerAgentId is required.';
      }
      if (!config.resolutionPrompt.trim()) {
        stepErrors.resolutionPrompt = 'resolutionPrompt is required.';
      }
      if (!Number.isFinite(config.maxRounds) || config.maxRounds < 1) {
        stepErrors.maxRounds = 'maxRounds must be >= 1.';
      }
    }

    if (step.behaviourType === 'RESOURCE') {
      const config = step.config as { resourcePath: string; contextKey: string };
      if (!config.resourcePath.startsWith('/schema/')) {
        stepErrors.resourcePath = 'resourcePath must start with /schema/.';
      }
      if (!config.contextKey.trim()) {
        stepErrors.contextKey = 'contextKey is required.';
      }
    }

    if (step.behaviourType === 'WORKFLOW_BEHAVIOUR') {
      const config = step.config as { workflowRef: string };
      if (!config.workflowRef.trim()) {
        stepErrors.workflowRef = 'workflowRef is required.';
      }
    }

    if (step.behaviourType === 'FAIL') {
      const config = step.config as { reason: string };
      if (!config.reason.trim()) {
        stepErrors.reason = 'reason is required.';
      }
    }

    if (step.behaviourType === 'BRANCH') {
      const config = step.config as BranchConfig;
      if (!config.contextKey.trim()) {
        stepErrors.contextKey = 'Branch context key is required.';
      }
      const resolveId = (id: string) => workflow.steps.some((s) => s.stepId.trim() === id.trim());
      for (const [key, targetId] of Object.entries(config.branches)) {
        if (!targetId.trim()) {
          stepErrors[`branch-${key}`] = 'Branch target step id is required.';
        } else if (!resolveId(targetId)) {
          stepErrors[`branch-${key}`] = `No step with id "${targetId}".`;
        }
      }
      if (!config.defaultBranch.trim()) {
        stepErrors.defaultBranch = 'Default branch target step id is required.';
      } else if (!resolveId(config.defaultBranch)) {
        stepErrors.defaultBranch = `No step with id "${config.defaultBranch}".`;
      }
    }

    if (step.behaviourType === 'RETRY_PREVIOUS') {
      const config = step.config as RetryPreviousConfig;
      if (!config.retryStepId.trim()) {
        stepErrors.retryStepId = 'retryStepId is required.';
      }
      if (!Number.isFinite(config.maxAttempts) || config.maxAttempts < 1) {
        stepErrors.maxAttempts = 'maxAttempts must be >= 1.';
      }
      if (config.fallback.trim() && !workflow.steps.some((s) => s.stepId.trim() === config.fallback.trim())) {
        stepErrors.fallback = `No step with id "${config.fallback}".`;
      }
    }

    if (Object.keys(stepErrors).length > 0) {
      errors.steps[index] = stepErrors;
    }
  });

  for (const [artifactId, artifact] of Object.entries(workflow.artifacts)) {
    const artifactErrors: Partial<Record<string, string>> = {};
    if (!artifactId.trim()) {
      artifactErrors.id = 'Artifact id is required.';
    }
    if (artifact.items.length === 0) {
      artifactErrors.items = 'Artifact must contain at least one item.';
    }
    artifact.items.forEach((item, index) => {
      if (!item.id.trim()) {
        artifactErrors[`item-${index}-id`] = 'Item id is required.';
      }
      if (!item.label.trim()) {
        artifactErrors[`item-${index}-label`] = 'Item label is required.';
      }
      if (!hasOptions(item)) {
        artifactErrors[`item-${index}-options`] = 'Choice items must include at least one option.';
      }
    });
    if (Object.keys(artifactErrors).length > 0) {
      errors.artifacts[artifactId] = artifactErrors;
    }
  }

  return errors;
}

function toValidationResult(errors: EditorValidation): ValidationResult {
  const issues: ValidationIssue[] = [];

  for (const [field, message] of Object.entries(errors.workflow)) {
    if (message) {
      issues.push({ path: `workflow.${field}`, message, severity: 'error' });
    }
  }

  for (const [index, stepErrors] of Object.entries(errors.steps)) {
    for (const [field, message] of Object.entries(stepErrors)) {
      if (message) {
        issues.push({ path: `steps[${index}].${field}`, message, severity: 'error' });
      }
    }
  }

  for (const [artifactId, artifactErrors] of Object.entries(errors.artifacts)) {
    for (const [field, message] of Object.entries(artifactErrors)) {
      if (message) {
        issues.push({ path: `artifacts.${artifactId}.${field}`, message, severity: 'error' });
      }
    }
  }

  for (const message of errors.global) {
    issues.push({ path: 'global', message, severity: 'error' });
  }

  return { valid: issues.length === 0, issues };
}

export function validateWorkflow(draft: WorkflowDefinition): ValidationResult {
  return toValidationResult(validateEditableWorkflow(draft));
}

/** Raw editor validation shape for UI grouping (client-side only). */
export function validateWorkflowEditor(draft: WorkflowDefinition): EditorValidation {
  return validateEditableWorkflow(draft);
}

export type { EditorValidation };
