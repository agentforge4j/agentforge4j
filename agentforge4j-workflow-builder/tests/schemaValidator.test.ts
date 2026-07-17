import { describe, expect, it } from 'vitest';
import type { WorkflowDefinition } from '../src/api/types';
import packageJson from '../package.json';
import {
  SUPPORTED_WORKFLOW_SCHEMA_VERSIONS,
  toRuntimeWorkflowDocument,
  validateAgainstSchema,
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
