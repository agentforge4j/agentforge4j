// SPDX-License-Identifier: Apache-2.0

import type { AgentRef, BlueprintJsonObject, BranchConfig, EditableStep, WorkflowDefinition } from '../api/types';

type CrossRefValidationResult = {
  issues: Array<{ path: string; message: string; severity: 'error' | 'warning' | 'info' }>;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function runtimeStepId(value: unknown): string | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  if (value.kind === 'STEP' && typeof value.stepId === 'string') {
    return value.stepId;
  }
  return undefined;
}

function flattenEditableSteps(workflow: WorkflowDefinition): Array<{ step: EditableStep; path: string }> {
  const flattened: Array<{ step: EditableStep; path: string }> = [];
  workflow.steps.forEach((step, index) => {
    flattened.push({ step, path: `steps[${index}]` });
  });
  for (const [blueprintId, body] of Object.entries(workflow.blueprintBodies ?? {})) {
    const steps = (body as BlueprintJsonObject).steps;
    if (Array.isArray(steps)) {
      steps.forEach((inner, index) => {
        const stepId = runtimeStepId(inner);
        if (!stepId) {
          return;
        }
        flattened.push({
          step: {
            stepId,
            name: isRecord(inner) && typeof inner.name === 'string' ? inner.name : stepId,
            behaviourType: 'INPUT',
            config: { artifactId: '', transition: 'AUTO' },
          },
          path: `blueprintBodies.${blueprintId}.steps[${index}]`,
        });
      });
    }
  }
  return flattened;
}

function agentIds(agentCatalog?: AgentRef[]): Set<string> | null {
  if (agentCatalog?.length) {
    return new Set(agentCatalog.map((agent) => agent.id.trim()).filter(Boolean));
  }
  return null;
}

function validateLoopConfig(
  loopConfig: Record<string, unknown>,
  path: string,
  issues: CrossRefValidationResult['issues'],
): void {
  const strategy = loopConfig.terminationStrategy;
  if (strategy === 'FOR_EACH') {
    const key = loopConfig.forEachContextKey;
    if (typeof key !== 'string' || !key.trim()) {
      issues.push({
        path: `${path}.forEachContextKey`,
        message: 'forEachContextKey is required when terminationStrategy is FOR_EACH.',
        severity: 'error',
      });
    }
  }
  if (strategy === 'EVALUATOR') {
    const agentId = loopConfig.evaluatorAgentId;
    if (typeof agentId !== 'string' || !agentId.trim()) {
      issues.push({
        path: `${path}.evaluatorAgentId`,
        message: 'evaluatorAgentId is required when terminationStrategy is EVALUATOR.',
        severity: 'error',
      });
    }
  }
}

export function validateCrossReferences(
  workflow: WorkflowDefinition,
  agentCatalog?: AgentRef[],
): CrossRefValidationResult {
  const issues: CrossRefValidationResult['issues'] = [];
  const knownAgents = agentIds(agentCatalog);
  const knownStepIds = new Set(workflow.steps.map((step) => step.stepId.trim()).filter(Boolean));
  const knownBlueprintIds = new Set(Object.keys(workflow.blueprintBodies ?? {}));
  const flattened = flattenEditableSteps(workflow);
  const orderedStepIds = flattened.map((entry) => entry.step.stepId.trim()).filter(Boolean);
  const seenStepIds = new Map<string, string>();

  if (!workflow.id.trim()) {
    issues.push({ path: 'workflow.id', message: 'Workflow id is required.', severity: 'error' });
  }
  if (!workflow.name.trim()) {
    issues.push({ path: 'workflow.name', message: 'Workflow name is required.', severity: 'error' });
  }

  for (const { step, path } of flattened) {
    const stepId = step.stepId.trim();
    if (stepId) {
      const priorPath = seenStepIds.get(stepId);
      if (priorPath) {
        issues.push({
          path: `${path}.stepId`,
          message: `Duplicate stepId "${stepId}" (also declared at ${priorPath}).`,
          severity: 'error',
        });
      } else {
        seenStepIds.set(stepId, `${path}.stepId`);
      }
    }
  }

  workflow.steps.forEach((step, index) => {
    const path = `steps[${index}]`;

    if (step.behaviourType === 'AGENT' || step.behaviourType === 'SPAR') {
      const config = step.config as { agentRef?: string };
      const agentRef = config.agentRef?.trim() ?? '';
      if (knownAgents && agentRef && !knownAgents.has(agentRef)) {
        issues.push({
          path: `${path}.config.agentRef`,
          message: `Agent reference "${agentRef}" was not found in the agent catalog.`,
          severity: 'error',
        });
      }
      if (step.stepPrompt !== undefined && !step.stepPrompt.trim()) {
        issues.push({
          path: `${path}.stepPrompt`,
          message: 'stepPrompt must be a non-blank string when present.',
          severity: 'warning',
        });
      }
    }

    if (step.behaviourType === 'WORKFLOW_BEHAVIOUR') {
      const config = step.config as { workflowRef?: string };
      if (!config.workflowRef?.trim()) {
        issues.push({
          path: `${path}.config.workflowRef`,
          message: 'workflowRef should be a non-blank string.',
          severity: 'warning',
        });
      }
    }

    if (step.behaviourType === 'INPUT') {
      const config = step.config as { artifactId?: string };
      const artifactId = config.artifactId?.trim() ?? '';
      if (artifactId && !workflow.artifacts[artifactId]) {
        issues.push({
          path: `${path}.config.artifactId`,
          message: `Artifact "${artifactId}" was not found in workflow.artifacts.`,
          severity: 'error',
        });
      }
    }

    if (step.behaviourType === 'BRANCH') {
      const config = step.config as BranchConfig;
      for (const [branchKey, target] of Object.entries(config.branches)) {
        const targetId = target.trim();
        if (!targetId) {
          continue;
        }
        if (knownBlueprintIds.has(targetId)) {
          continue;
        }
        if (!knownStepIds.has(targetId)) {
          issues.push({
            path: `${path}.config.branches.${branchKey}`,
            message: `Branch target "${targetId}" was not found in workflow steps or blueprints.`,
            severity: 'error',
          });
        }
      }
      const defaultTarget = config.defaultBranch.trim();
      if (defaultTarget && !knownBlueprintIds.has(defaultTarget) && !knownStepIds.has(defaultTarget)) {
        issues.push({
          path: `${path}.config.defaultBranch`,
          message: `Default branch target "${defaultTarget}" was not found in workflow steps or blueprints.`,
          severity: 'error',
        });
      }
    }

    if (step.behaviourType === 'RETRY_PREVIOUS') {
      const config = step.config as { retryStepId?: string };
      const retryStepId = config.retryStepId?.trim() ?? '';
      if (!retryStepId) {
        issues.push({
          path: `${path}.config.retryStepId`,
          message: 'retryStepId is required.',
          severity: 'error',
        });
      } else {
        const currentStepId = step.stepId.trim();
        const retryIndex = orderedStepIds.indexOf(currentStepId);
        const targetIndex = orderedStepIds.indexOf(retryStepId);
        if (targetIndex < 0 || (retryIndex >= 0 && targetIndex >= retryIndex)) {
          issues.push({
            path: `${path}.config.retryStepId`,
            message: `retryStepId "${retryStepId}" must reference a step that appears before this step.`,
            severity: 'error',
          });
        }
      }
    }
  });

  for (const [blueprintId, body] of Object.entries(workflow.blueprintBodies ?? {})) {
    const behaviour = (body as BlueprintJsonObject).behaviour;
    if (isRecord(behaviour) && isRecord(behaviour.loopConfig)) {
      validateLoopConfig(behaviour.loopConfig, `blueprintBodies.${blueprintId}.behaviour.loopConfig`, issues);
    }
  }

  return { issues };
}
