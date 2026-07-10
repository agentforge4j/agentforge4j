// SPDX-License-Identifier: Apache-2.0

import JSZip from 'jszip';
import type {
  BlueprintJsonObject,
  EditableArtifact,
  EditableStep,
  StepConfig,
  TopLevelScheduleEntry,
  WorkflowDefinition,
} from '../../api/types';
import {
  normalizeRuntimeDocumentForSchema,
  toRuntimeWorkflowDocument,
  validateAgainstSchema,
  validateSchemaVersion,
} from '../../validation/schemaValidator';
import { sanitizeObject, WorkflowParseError } from '../core';

export { sanitizeObject } from '../core';

const ZIP_SIZE_LIMIT = 5 * 1024 * 1024;
const STEP_COUNT_LIMIT = 200;
const ENTRY_NAME_PATTERN = /^[a-zA-Z0-9._-]+\/[a-zA-Z0-9._-]+$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function assertSafeEntryName(name: string): void {
  if (name.includes('..') || name.startsWith('/') || !ENTRY_NAME_PATTERN.test(name)) {
    throw new WorkflowParseError(`Unsafe ZIP entry name: ${name}`);
  }
}

function runtimeStepToEditable(
  step: Record<string, unknown>,
  prompts: Record<string, string>,
  collectedSteps: EditableStep[],
): EditableStep {
  const behaviour = step.behaviour as Record<string, unknown>;
  const type = String(behaviour.type ?? '');
  const stepId = String(step.stepId ?? '');
  const contextMapping = isRecord(step.contextMapping)
    ? {
        inputKeys: Array.isArray(step.contextMapping.inputKeys)
          ? step.contextMapping.inputKeys.map(String)
          : [],
        outputKeys: Array.isArray(step.contextMapping.outputKeys)
          ? step.contextMapping.outputKeys.map(String)
          : [],
      }
    : { inputKeys: [], outputKeys: [] };

  const base = {
    stepId,
    name: String(step.name ?? stepId),
    stepPrompt: prompts[stepId],
    contextMapping,
  };

  const registerNested = (executable: unknown): string | undefined => {
    if (!isRecord(executable)) {
      return undefined;
    }
    if (executable.kind === 'BLUEPRINT_REF' && typeof executable.blueprintId === 'string') {
      return executable.blueprintId;
    }
    if (executable.kind === 'STEP') {
      const nested = runtimeStepToEditable(executable, prompts, collectedSteps);
      if (!collectedSteps.some((existing) => existing.stepId === nested.stepId)) {
        collectedSteps.push(nested);
      }
      return nested.stepId;
    }
    return undefined;
  };

  let config: StepConfig;
  switch (type) {
    case 'INPUT':
      config = {
        artifactId: String(behaviour.artifactId ?? ''),
        transition: String(behaviour.transition ?? 'AUTO') as 'AUTO',
      };
      return { ...base, behaviourType: 'INPUT', config };
    case 'AGENT': {
      const retryPolicy = isRecord(behaviour.retryPolicy) ? behaviour.retryPolicy : undefined;
      config = {
        agentRef: String(behaviour.agentRef ?? ''),
        transition: String(behaviour.transition ?? 'AUTO') as 'AUTO',
        maxRetries: typeof retryPolicy?.maxAttempts === 'number' ? retryPolicy.maxAttempts : 0,
      };
      return { ...base, behaviourType: 'AGENT', config };
    }
    case 'SPAR': {
      const sparConfig = isRecord(behaviour.sparConfig) ? behaviour.sparConfig : {};
      config = {
        agentRef: String(behaviour.agentRef ?? ''),
        challengerAgentId: String(sparConfig.challengerAgentId ?? ''),
        maxRounds: typeof sparConfig.maxRounds === 'number' ? sparConfig.maxRounds : 1,
        resolutionPrompt: String(sparConfig.resolutionPrompt ?? ''),
        transition: String(behaviour.transition ?? 'AUTO') as 'AUTO',
      };
      return { ...base, behaviourType: 'SPAR', config };
    }
    case 'WORKFLOW':
      config = {
        workflowRef: String(behaviour.workflowRef ?? ''),
        transition: String(behaviour.transition ?? 'AUTO') as 'AUTO',
      };
      return { ...base, behaviourType: 'WORKFLOW_BEHAVIOUR', config };
    case 'RESOURCE':
      config = {
        resourcePath: String(behaviour.resourcePath ?? '/schema/'),
        contextKey: String(behaviour.contextKey ?? ''),
        transition: String(behaviour.transition ?? 'AUTO') as 'AUTO',
      };
      return { ...base, behaviourType: 'RESOURCE', config };
    case 'FAIL':
      config = { reason: String(behaviour.reason ?? '') };
      return { ...base, behaviourType: 'FAIL', config };
    case 'RETRY_PREVIOUS': {
      const fallbackId = registerNested(behaviour.fallback);
      config = {
        retryStepId: String(behaviour.retryStepId ?? ''),
        retryMode: String(behaviour.retryMode ?? 'SINGLE_STEP') as 'SINGLE_STEP',
        maxAttempts: typeof behaviour.maxAttempts === 'number' ? behaviour.maxAttempts : 1,
        fallback: fallbackId ?? '',
      };
      return { ...base, behaviourType: 'RETRY_PREVIOUS', config };
    }
    case 'BRANCH': {
      const branches: Record<string, string> = {};
      if (isRecord(behaviour.branches)) {
        for (const [key, target] of Object.entries(behaviour.branches)) {
          const targetId = registerNested(target);
          if (targetId) {
            branches[key] = targetId;
          }
        }
      }
      const defaultBranch = registerNested(behaviour.defaultBranch) ?? '';
      config = {
        contextKey: String(behaviour.contextKey ?? ''),
        branches,
        defaultBranch,
      };
      return { ...base, behaviourType: 'BRANCH', config };
    }
    default:
      config = { artifactId: '', transition: 'AUTO' };
      return { ...base, behaviourType: 'INPUT', config };
  }
}

function runtimeDocumentToBuilder(
  runtimeDoc: Record<string, unknown>,
  artifacts: Record<string, EditableArtifact>,
  blueprintBodies: Record<string, BlueprintJsonObject>,
  prompts: Record<string, string>,
): WorkflowDefinition {
  const steps: EditableStep[] = [];
  const topLevelSchedule: TopLevelScheduleEntry[] = [];
  const runtimeSteps = Array.isArray(runtimeDoc.steps) ? runtimeDoc.steps : [];

  for (const executable of runtimeSteps) {
    if (!isRecord(executable)) {
      continue;
    }
    if (executable.kind === 'BLUEPRINT_REF' && typeof executable.blueprintId === 'string') {
      topLevelSchedule.push({ kind: 'BLUEPRINT_REF', blueprintId: executable.blueprintId });
      continue;
    }
    if (executable.kind === 'STEP') {
      const editable = runtimeStepToEditable(executable, prompts, steps);
      const stepIndex = steps.length;
      steps.push(editable);
      topLevelSchedule.push({ kind: 'STEP', stepIndex });
    }
  }

  return sanitizeObject({
    id: String(runtimeDoc.id ?? ''),
    name: String(runtimeDoc.name ?? ''),
    description: String(runtimeDoc.description ?? ''),
    steps,
    artifacts,
    blueprintBodies,
    topLevelSchedule: topLevelSchedule.length > 0 ? topLevelSchedule : undefined,
  });
}

function collectStepPrompts(workflow: WorkflowDefinition): Map<string, string> {
  const prompts = new Map<string, string>();
  for (const step of workflow.steps) {
    if (step.stepPrompt?.trim()) {
      prompts.set(step.stepId, step.stepPrompt.trim());
    }
  }
  return prompts;
}

function stripPromptsFromRuntimeDocument(doc: Record<string, unknown>): Record<string, unknown> {
  const clone = structuredClone(doc);
  const stripExecutables = (executables: unknown): void => {
    if (!Array.isArray(executables)) {
      return;
    }
    for (const executable of executables) {
      if (!isRecord(executable)) {
        continue;
      }
      if (executable.kind === 'STEP') {
        delete executable.stepPrompt;
        if (isRecord(executable.behaviour) && executable.behaviour.type === 'BRANCH') {
          if (isRecord(executable.behaviour.branches)) {
            for (const branch of Object.values(executable.behaviour.branches)) {
              stripExecutables([branch]);
            }
          }
          if (executable.behaviour.defaultBranch) {
            stripExecutables([executable.behaviour.defaultBranch]);
          }
        }
        if (isRecord(executable.behaviour) && executable.behaviour.type === 'RETRY_PREVIOUS') {
          stripExecutables([executable.behaviour.fallback]);
        }
      }
    }
  };
  stripExecutables(clone.steps);
  return clone;
}

/** Falls back to a plain name when the draft has no id yet, matching downloadWorkflowJson's convention. */
function workflowFileBase(workflow: WorkflowDefinition): string {
  return workflow.id.trim() || 'workflow';
}

export async function buildWorkflowZipBlob(workflow: WorkflowDefinition): Promise<Blob> {
  const zip = new JSZip();
  const folderName = `${workflowFileBase(workflow)}.workflow`;
  const folder = zip.folder(folderName);
  if (!folder) {
    throw new WorkflowParseError('Failed to create ZIP folder');
  }

  const runtimeDoc = normalizeRuntimeDocumentForSchema(
    workflow,
    stripPromptsFromRuntimeDocument(toRuntimeWorkflowDocument(workflow)),
  );
  folder.file('workflow.json', JSON.stringify(runtimeDoc, null, 2));

  for (const [artifactId, artifact] of Object.entries(workflow.artifacts)) {
    folder.file(`${artifactId}.artifact.json`, JSON.stringify(artifact, null, 2));
  }

  for (const [blueprintId, blueprint] of Object.entries(workflow.blueprintBodies ?? {})) {
    folder.file(`${blueprintId}.blueprint.json`, JSON.stringify(blueprint, null, 2));
  }

  for (const [stepId, prompt] of collectStepPrompts(workflow)) {
    folder.file(`${stepId}.step.prompt.md`, prompt);
  }

  return zip.generateAsync({ type: 'blob', compression: 'DEFLATE', mimeType: 'application/zip' });
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export async function exportWorkflowZip(workflow: WorkflowDefinition): Promise<void> {
  const blob = await buildWorkflowZipBlob(workflow);
  triggerDownload(blob, `${workflowFileBase(workflow)}.workflow.zip`);
}

function countSteps(workflow: WorkflowDefinition): number {
  let count = workflow.steps.length;
  for (const body of Object.values(workflow.blueprintBodies ?? {})) {
    const steps = (body as BlueprintJsonObject).steps;
    if (Array.isArray(steps)) {
      count += steps.length;
    }
  }
  return count;
}

export async function importWorkflowZip(file: File): Promise<WorkflowDefinition> {
  if (file.size > ZIP_SIZE_LIMIT) {
    throw new WorkflowParseError('ZIP file exceeds 5 MB limit');
  }

  const zip = await JSZip.loadAsync(file);
  const entries = Object.keys(zip.files).filter((name) => !zip.files[name]?.dir);

  for (const name of entries) {
    assertSafeEntryName(name);
  }

  const workflowJsonPath = entries.find((name) => name.endsWith('/workflow.json'));
  if (!workflowJsonPath) {
    throw new WorkflowParseError('workflow.json not found in ZIP');
  }

  const workflowJsonText = await zip.file(workflowJsonPath)!.async('text');
  let runtimeDoc: Record<string, unknown>;
  try {
    runtimeDoc = sanitizeObject(JSON.parse(workflowJsonText) as Record<string, unknown>);
  } catch {
    throw new WorkflowParseError('Invalid workflow.json in ZIP');
  }

  // Checked against the raw document, before conversion to the internal draft shape: the draft
  // carries no schemaVersion of its own, and re-export always regenerates one, so validating
  // anything past this point would silently accept whatever version the ZIP actually declared.
  const schemaVersionResult = validateSchemaVersion(runtimeDoc);
  if (!schemaVersionResult.valid) {
    throw new WorkflowParseError(schemaVersionResult.errors[0]?.message ?? 'Unsupported schemaVersion');
  }

  const folderPrefix = workflowJsonPath.slice(0, workflowJsonPath.lastIndexOf('/') + 1);
  const siblingEntries = entries.filter((name) => name.startsWith(folderPrefix) && name !== workflowJsonPath);

  const artifacts: Record<string, EditableArtifact> = {};
  const blueprintBodies: Record<string, BlueprintJsonObject> = {};
  const prompts: Record<string, string> = {};

  for (const entryName of siblingEntries) {
    const relativeName = entryName.slice(folderPrefix.length);
    if (relativeName.endsWith('.artifact.json')) {
      const artifactId = relativeName.slice(0, -'.artifact.json'.length);
      const text = await zip.file(entryName)!.async('text');
      artifacts[artifactId] = sanitizeObject(JSON.parse(text) as EditableArtifact);
      continue;
    }
    if (relativeName.endsWith('.blueprint.json')) {
      const blueprintId = relativeName.slice(0, -'.blueprint.json'.length);
      const text = await zip.file(entryName)!.async('text');
      blueprintBodies[blueprintId] = sanitizeObject(JSON.parse(text) as BlueprintJsonObject);
      continue;
    }
    if (relativeName.endsWith('.step.prompt.md')) {
      const stepId = relativeName.slice(0, -'.step.prompt.md'.length);
      prompts[stepId] = await zip.file(entryName)!.async('text');
    }
  }

  const workflow = runtimeDocumentToBuilder(runtimeDoc, artifacts, blueprintBodies, prompts);

  for (const step of workflow.steps) {
    if (prompts[step.stepId]) {
      step.stepPrompt = prompts[step.stepId];
    }
  }

  if (countSteps(workflow) > STEP_COUNT_LIMIT) {
    throw new WorkflowParseError('Workflow exceeds 200 steps');
  }

  const schemaResult = validateAgainstSchema(workflow);
  if (!schemaResult.valid) {
    throw new WorkflowParseError(schemaResult.errors[0]?.message ?? 'Schema validation failed');
  }

  return workflow;
}
