// SPDX-License-Identifier: Apache-2.0

import Ajv, { type ErrorObject } from 'ajv';
import addFormats from 'ajv-formats';
import type { WorkflowDefinition } from '../api/types';
import { exportStepJson } from '../model/mapper';
import agentSchema from '../schemas/agent.schema.json';
import artifactSchema from '../schemas/artifact.schema.json';
import blueprintSchema from '../schemas/blueprint.schema.json';
import workflowSchema from '../generated/workflow.schema.json';

type SchemaValidationResult = {
  valid: boolean;
  errors: Array<{ path: string; message: string }>;
};

/**
 * Workflow definition format version this builder writes into every exported document.
 * `schemaVersion` is a required property of the runtime workflow schema; consumers accept only
 * schema versions they know and reject anything else.
 */
export const WORKFLOW_SCHEMA_VERSION = 1;

/**
 * Workflow schema versions this builder can read on import. A superset of
 * {@link WORKFLOW_SCHEMA_VERSION} only once the builder supports reading an older format after a
 * framework minor moves the write version forward (design §4 rule 3); today the read and write
 * versions coincide.
 */
export const SUPPORTED_WORKFLOW_SCHEMA_VERSIONS: readonly number[] = [WORKFLOW_SCHEMA_VERSION];

const ajv = new Ajv({
  strict: false,
  allErrors: true,
  validateSchema: false,
  code: { esm: true, optimize: false },
});
addFormats(ajv);
ajv.addSchema(blueprintSchema, 'blueprint.schema.json');
ajv.addSchema(workflowSchema, 'workflow.schema.json');
ajv.addSchema(artifactSchema, 'artifact.schema.json');
ajv.addSchema(agentSchema, 'agent.schema.json');

const validateRuntimeDocument = ajv.compile(workflowSchema);

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function normalizeExecutable(value: unknown): void {
  if (!isRecord(value)) {
    return;
  }
  if (value.kind === 'STEP') {
    delete value.stepPrompt;
    if (isRecord(value.behaviour)) {
      const behaviour = value.behaviour;
      if (behaviour.type === 'AGENT' || behaviour.type === 'SPAR') {
        const retryPolicy = behaviour.retryPolicy;
        if (
          isRecord(retryPolicy) &&
          (retryPolicy.allowRetry === false || retryPolicy.maxAttempts === 0)
        ) {
          delete behaviour.retryPolicy;
        }
      }
      if (behaviour.type === 'BRANCH') {
        if (isRecord(behaviour.branches)) {
          for (const branch of Object.values(behaviour.branches)) {
            normalizeExecutable(branch);
          }
        }
        normalizeExecutable(behaviour.defaultBranch);
      }
      if (behaviour.type === 'RETRY_PREVIOUS') {
        normalizeExecutable(behaviour.fallback);
      }
    }
  }
}

export function normalizeRuntimeDocumentForSchema(
  workflow: WorkflowDefinition,
  doc: Record<string, unknown>,
): Record<string, unknown> {
  const normalized = structuredClone(doc) as Record<string, unknown>;
  if (!workflow.id.trim()) {
    delete normalized.id;
  }
  if (Array.isArray(normalized.steps)) {
    for (const step of normalized.steps) {
      normalizeExecutable(step);
    }
  }
  return normalized;
}

/** Convert builder draft shape to runtime workflow.json document for schema validation. */
export function toRuntimeWorkflowDocument(workflow: WorkflowDefinition): Record<string, unknown> {
  const steps: unknown[] = [];
  if (workflow.topLevelSchedule?.length) {
    for (const entry of workflow.topLevelSchedule) {
      if (entry.kind === 'STEP') {
        const step = workflow.steps[entry.stepIndex];
        if (step) {
          steps.push(exportStepJson(workflow, step));
        }
      } else if (entry.kind === 'BLUEPRINT_REF') {
        steps.push({ kind: 'BLUEPRINT_REF', blueprintId: entry.blueprintId });
      }
    }
  } else {
    for (const step of workflow.steps) {
      steps.push(exportStepJson(workflow, step));
    }
  }

  const doc: Record<string, unknown> = {
    kind: 'WORKFLOW',
    schemaVersion: WORKFLOW_SCHEMA_VERSION,
    id: workflow.id,
    name: workflow.name,
    steps,
  };
  if (workflow.description) {
    doc.description = workflow.description;
  }
  return doc;
}

/**
 * Validates the `schemaVersion` declared in a raw runtime workflow document — the document as
 * parsed from an imported `.workflow.zip`, before it is converted into this builder's internal
 * draft shape.
 *
 * This check must run against the raw document, not the internal draft: {@link
 * toRuntimeWorkflowDocument} always regenerates a document carrying the builder's own {@link
 * WORKFLOW_SCHEMA_VERSION}, so validating the round-tripped document would silently accept (and
 * re-stamp) a document declaring an unknown or missing version instead of rejecting it.
 */
export function validateSchemaVersion(doc: Record<string, unknown>): SchemaValidationResult {
  const declared = doc.schemaVersion;
  if (declared === undefined || declared === null) {
    return {
      valid: false,
      errors: [
        {
          path: 'workflow.schemaVersion',
          message: `must declare a schemaVersion; supported: ${SUPPORTED_WORKFLOW_SCHEMA_VERSIONS.join(', ')}`,
        },
      ],
    };
  }
  if (typeof declared !== 'number' || !Number.isInteger(declared)) {
    return {
      valid: false,
      errors: [
        {
          path: 'workflow.schemaVersion',
          message: `schemaVersion '${String(declared)}' must be an integer; supported: ${SUPPORTED_WORKFLOW_SCHEMA_VERSIONS.join(', ')}`,
        },
      ],
    };
  }
  if (!SUPPORTED_WORKFLOW_SCHEMA_VERSIONS.includes(declared)) {
    return {
      valid: false,
      errors: [
        {
          path: 'workflow.schemaVersion',
          message: `schemaVersion ${declared} is not supported; supported: ${SUPPORTED_WORKFLOW_SCHEMA_VERSIONS.join(', ')}`,
        },
      ],
    };
  }
  return { valid: true, errors: [] };
}

function mapAjvPath(error: ErrorObject): string {
  const instancePath = error.instancePath || '/';
  if (instancePath === '/id' || instancePath === '/name' || instancePath === '/description') {
    return `workflow.${instancePath.slice(1)}`;
  }
  if (instancePath.startsWith('/steps/')) {
    return instancePath.slice(1).replace(/\//g, '.');
  }
  if (instancePath === '/steps') {
    return 'steps';
  }
  if (instancePath === '/') {
    return 'workflow';
  }
  return instancePath.startsWith('/') ? instancePath.slice(1).replace(/\//g, '.') : instancePath;
}

export function validateAgainstSchema(workflow: WorkflowDefinition): SchemaValidationResult {
  if (!workflow.id.trim()) {
    return {
      valid: false,
      errors: [{ path: 'workflow.id', message: "must have required property 'id'" }],
    };
  }

  const runtimeDoc = normalizeRuntimeDocumentForSchema(workflow, toRuntimeWorkflowDocument(workflow));
  const valid = validateRuntimeDocument(runtimeDoc) as boolean;
  if (valid) {
    return { valid: true, errors: [] };
  }

  const errors = (validateRuntimeDocument.errors ?? []).map((error) => ({
    path: mapAjvPath(error),
    message: error.message ?? 'Schema validation failed',
  }));
  return { valid: false, errors };
}
