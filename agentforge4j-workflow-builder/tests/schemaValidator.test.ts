import { describe, expect, it } from 'vitest';
import type { WorkflowDefinition } from '../src/api/types';
import packageJson from '../package.json';
import {
  SUPPORTED_WORKFLOW_SCHEMA_VERSIONS,
  toRuntimeWorkflowDocument,
  validateAgainstSchema,
  validateRuntimeDocument,
  validateSchemaVersion,
  WORKFLOW_SCHEMA_VERSION,
} from '../src/validation/schemaValidator';

function minimalWorkflow(overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition {
  return {
    id: 'demo-flow',
    name: 'Demo',
    description: '',
    steps: [
      {
        stepId: 'ask-user-1',
        name: 'Ask',
        behaviourType: 'INPUT',
        config: { artifactId: 'artifact-ask-user-1', transition: 'AUTO' },
      },
      {
        stepId: 'ai-step-1',
        name: 'Think',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-a', transition: 'AUTO', maxRetries: 0 },
      },
    ],
    artifacts: {
      'artifact-ask-user-1': {
        id: 'artifact-ask-user-1',
        items: [{ id: 'value', type: 'TEXT', label: 'Value', required: true }],
      },
    },
    ...overrides,
  };
}

describe('validateAgainstSchema', () => {
  it('passes for a valid minimal workflow definition', () => {
    const result = validateAgainstSchema(minimalWorkflow());
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('fails when workflow id is missing in the runtime document', () => {
    const result = validateAgainstSchema(minimalWorkflow({ id: '' }));
    expect(result.valid).toBe(false);
    expect(result.errors.some((error) => error.path.includes('id'))).toBe(true);
  });

  it('fails when a step has an unrecognised executable kind', () => {
    const broken = minimalWorkflow();
    broken.steps = [
      {
        stepId: 'bad-step',
        name: 'Bad',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-a', transition: 'AUTO' },
      },
    ];
    const result = validateAgainstSchema({
      ...broken,
      steps: broken.steps.map((step) => ({
        ...step,
        behaviourType: 'INVALID' as 'AGENT',
      })),
    });
    expect(result.valid).toBe(false);
  });

  // The exported RetryPolicy shrank from five fields to three (allowRetry,
  // allowRetryFromPrevious, maxAttempts); allowAgentSwap/allowPromptOverride were removed as
  // unsupported, decorative promises with no backing runtime operation, and the canonical
  // workflow.schema.json RetryPolicy $def now declares additionalProperties: false. An AGENT step
  // with maxRetries 0 exports the disabled policy, which normalizeExecutable strips entirely
  // before validation (allowRetry === false) — so it never actually exercises the RetryPolicy
  // $def. Only maxRetries > 0 emits a retryPolicy object that survives normalization and reaches
  // the schema, so this is the one shape that proves the exporter's output matches the current
  // schema end to end, through the real production path (exportStepJson →
  // toRuntimeWorkflowDocument → validateAgainstSchema).
  it('passes schema validation for an AGENT step with an active retry policy', () => {
    const workflow = minimalWorkflow();
    workflow.steps = workflow.steps.map((step) =>
      step.stepId === 'ai-step-1'
        ? { ...step, config: { agentRef: 'agent-a', transition: 'AUTO', maxRetries: 2 } }
        : step,
    );
    const result = validateAgainstSchema(workflow);
    expect(result.errors).toHaveLength(0);
    expect(result.valid).toBe(true);
  });

  // Permanent negative control, proving the positive test above is meaningful: a raw runtime
  // document (the shape a foreign producer's `.workflow.zip`, or this exporter before this fix,
  // could still emit) that carries either removed field is genuinely rejected by the canonical
  // schema this build was synced from — not merely by construction of this builder's own,
  // already-fixed exporter. Goes through validateRuntimeDocument directly (bypassing
  // exportStepJson, which can no longer produce this shape) so it exercises the schema's own
  // additionalProperties: false constraint on the RetryPolicy $def, independent of the exporter.
  it.each([
    ['allowAgentSwap', { allowRetry: true, allowRetryFromPrevious: false, allowAgentSwap: false, maxAttempts: 2 }],
    ['allowPromptOverride', { allowRetry: true, allowRetryFromPrevious: false, allowPromptOverride: false, maxAttempts: 2 }],
    [
      'both removed fields (the original five-field shape)',
      {
        allowRetry: true,
        allowRetryFromPrevious: false,
        allowAgentSwap: false,
        allowPromptOverride: false,
        maxAttempts: 2,
      },
    ],
  ])('rejects a retryPolicy still carrying %s', (_label, retryPolicy) => {
    const workflow = minimalWorkflow();
    workflow.steps = workflow.steps.map((step) =>
      step.stepId === 'ai-step-1'
        ? { ...step, config: { agentRef: 'agent-a', transition: 'AUTO', maxRetries: 2 } }
        : step,
    );
    const doc = toRuntimeWorkflowDocument(workflow);
    const steps = doc.steps as Array<Record<string, unknown>>;
    const agentStepDoc = steps.find((s) => s.stepId === 'ai-step-1')!;
    (agentStepDoc.behaviour as Record<string, unknown>).retryPolicy = retryPolicy;

    const result = validateRuntimeDocument(doc);

    expect(result.valid).toBe(false);
    expect(
      result.errors.some(
        (error) =>
          error.path.includes('retryPolicy') &&
          error.message.toLowerCase().includes('additional propert'),
      ),
    ).toBe(true);
  });
});

describe('validateSchemaVersion', () => {
  it('rejects a document with no schemaVersion', () => {
    const result = validateSchemaVersion({ kind: 'WORKFLOW', id: 'x', name: 'X', steps: [] });
    expect(result.valid).toBe(false);
    expect(result.errors[0]?.message).toContain('must declare a schemaVersion');
  });

  it('rejects a non-integer schemaVersion', () => {
    const result = validateSchemaVersion({
      kind: 'WORKFLOW',
      schemaVersion: 'one',
      id: 'x',
      name: 'X',
      steps: [],
    });
    expect(result.valid).toBe(false);
    expect(result.errors[0]?.message).toContain('must be an integer');
  });

  it('rejects a schemaVersion outside the supported set', () => {
    const result = validateSchemaVersion({
      kind: 'WORKFLOW',
      schemaVersion: 2,
      id: 'x',
      name: 'X',
      steps: [],
    });
    expect(result.valid).toBe(false);
    expect(result.errors[0]?.message).toContain('schemaVersion 2 is not supported');
  });

  it('accepts a document declaring a supported schemaVersion', () => {
    const result = validateSchemaVersion({
      kind: 'WORKFLOW',
      schemaVersion: WORKFLOW_SCHEMA_VERSION,
      id: 'x',
      name: 'X',
      steps: [],
    });
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('exported documents always declare a schemaVersion this same builder accepts on import', () => {
    // Contract: export and import must agree on the version this builder writes — a document it
    // exports must never be a version its own import path would reject.
    const doc = toRuntimeWorkflowDocument(minimalWorkflow());
    expect(doc.schemaVersion).toBe(WORKFLOW_SCHEMA_VERSION);
    expect(SUPPORTED_WORKFLOW_SCHEMA_VERSIONS).toContain(doc.schemaVersion);
    expect(validateSchemaVersion(doc).valid).toBe(true);
  });

  it('the published manifest declares the same supported set as this builder actually enforces', () => {
    // Regression guard: package.json's agentforge4j.supportedSchemaVersions is a second,
    // independently-hardcoded literal read by the release-builder guard (manifest-field
    // presence only, per design) — nothing else cross-checks it against the value this
    // builder's import path actually enforces, so a future schemaVersion bump could update
    // one and silently miss the other.
    expect(packageJson.agentforge4j.supportedSchemaVersions).toEqual([...SUPPORTED_WORKFLOW_SCHEMA_VERSIONS]);
  });
});
