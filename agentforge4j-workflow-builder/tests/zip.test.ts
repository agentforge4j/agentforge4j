// @vitest-environment jsdom
import JSZip from 'jszip';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import type { WorkflowDefinition } from '../src/api/types';
import { WorkflowParseError } from '../src/io/core';
import {
  buildWorkflowZipBlob,
  exportWorkflowZip,
  importWorkflowZip,
  sanitizeObject,
} from '../src/io/browser/zip';
import { canvasToWorkflow, newStepId } from '../src/model/mapper';
import { validateBlueprintDocument } from '../src/validation/schemaValidator';

function sampleWorkflow(): WorkflowDefinition {
  return {
    id: 'zip-demo',
    name: 'ZIP Demo',
    description: 'Demo workflow',
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
        stepPrompt: 'Do the work',
      },
    ],
    artifacts: {
      'artifact-ask-user-1': {
        id: 'artifact-ask-user-1',
        items: [{ id: 'value', type: 'TEXT', label: 'Value', required: true }],
      },
    },
  };
}

describe('workflow zip io', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('buildWorkflowZipBlob produces an application/zip blob', async () => {
    const blob = await buildWorkflowZipBlob(sampleWorkflow());
    expect(blob.type).toBe('application/zip');
  });

  it('buildWorkflowZipBlob falls back to a plain folder name when the workflow has no id yet', async () => {
    const blob = await buildWorkflowZipBlob({ ...sampleWorkflow(), id: '' });
    const zip = await JSZip.loadAsync(blob);
    expect(Object.keys(zip.files).some((name) => name.startsWith('workflow.workflow/'))).toBe(true);
  });

  it('exportWorkflowZip triggers a download with a zip blob', async () => {
    const click = vi.fn();
    vi.spyOn(document, 'createElement').mockReturnValue({
      click,
      href: '',
      download: '',
    } as unknown as HTMLAnchorElement);

    await exportWorkflowZip(sampleWorkflow());

    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  it('falls back to a plain filename when exporting a workflow with no id yet', async () => {
    const anchor = { click: vi.fn(), href: '', download: '' } as unknown as HTMLAnchorElement;
    vi.spyOn(document, 'createElement').mockReturnValue(anchor);

    await exportWorkflowZip({ ...sampleWorkflow(), id: '' });

    expect(anchor.download).toBe('workflow.workflow.zip');
  });

  it('imports a valid zip and returns the workflow id', async () => {
    const blob = await buildWorkflowZipBlob(sampleWorkflow());
    const file = new File([blob], 'zip-demo.workflow.zip', { type: 'application/zip' });
    const imported = await importWorkflowZip(file);
    expect(imported.id).toBe('zip-demo');
    expect(imported.steps).toHaveLength(2);
    expect(imported.steps[1]?.stepPrompt).toBe('Do the work');
  });

  it('rejects zip files over 5 MB', async () => {
    const file = { size: 6 * 1024 * 1024, name: 'big.zip' } as File;
    await expect(importWorkflowZip(file)).rejects.toThrow(WorkflowParseError);
  });

  it('rejects zip entries containing path traversal', async () => {
    const zip = new JSZip();
    zip.file('demo.workflow/../escape', 'bad');
    zip.file('demo.workflow/workflow.json', JSON.stringify({ kind: 'WORKFLOW', id: 'demo', name: 'Demo', steps: [] }));
    const blob = await zip.generateAsync({ type: 'blob' });
    const file = new File([blob], 'bad.zip', { type: 'application/zip' });
    await expect(importWorkflowZip(file)).rejects.toThrow(WorkflowParseError);
  });

  it('rejects zip files missing workflow.json', async () => {
    const zip = new JSZip();
    zip.file('demo.workflow/readme.txt', 'missing workflow json');
    const blob = await zip.generateAsync({ type: 'blob' });
    const file = new File([blob], 'missing.json.zip', { type: 'application/zip' });
    await expect(importWorkflowZip(file)).rejects.toThrow(/workflow\.json not found/);
  });

  it('rejects a workflow.json missing schemaVersion, on the raw document rather than the re-exported one', async () => {
    // Regression: toRuntimeWorkflowDocument always writes the current schemaVersion when the
    // draft is re-exported, so a check against that regenerated document would never see what
    // the imported file actually declared. This must reject before draft conversion.
    const zip = new JSZip();
    zip.file(
      'demo.workflow/workflow.json',
      JSON.stringify({ kind: 'WORKFLOW', id: 'demo', name: 'Demo', steps: [] }),
    );
    const blob = await zip.generateAsync({ type: 'blob' });
    const file = new File([blob], 'no-version.zip', { type: 'application/zip' });
    await expect(importWorkflowZip(file)).rejects.toThrow(/must declare a schemaVersion/);
  });

  it('rejects a workflow.json declaring an unsupported schemaVersion', async () => {
    const zip = new JSZip();
    zip.file(
      'demo.workflow/workflow.json',
      JSON.stringify({ kind: 'WORKFLOW', schemaVersion: 2, id: 'demo', name: 'Demo', steps: [] }),
    );
    const blob = await zip.generateAsync({ type: 'blob' });
    const file = new File([blob], 'future-version.zip', { type: 'application/zip' });
    await expect(importWorkflowZip(file)).rejects.toThrow(/schemaVersion 2 is not supported/);
  });

  it('rejects a workflow.json declaring a non-integer schemaVersion', async () => {
    const zip = new JSZip();
    zip.file(
      'demo.workflow/workflow.json',
      JSON.stringify({ kind: 'WORKFLOW', schemaVersion: 'one', id: 'demo', name: 'Demo', steps: [] }),
    );
    const blob = await zip.generateAsync({ type: 'blob' });
    const file = new File([blob], 'non-integer-version.zip', { type: 'application/zip' });
    await expect(importWorkflowZip(file)).rejects.toThrow(/must be an integer/);
  });

  it('sanitizeObject strips dangerous keys', () => {
    const cleaned = sanitizeObject({
      id: 'safe',
      __proto__: { polluted: true },
      nested: {
        constructor: { bad: true },
        prototype: { bad: true },
        ok: 1,
      },
    });
    expect(cleaned).toEqual({ id: 'safe', nested: { ok: 1 } });
  });
});

describe('blueprint export retryPolicy shape', () => {
  // CF-001: exportStepJson's RetryPolicy shrink (see mapper.test.ts) only ever ran for the
  // top-level workflow.json, which additionally passes through normalizeRuntimeDocumentForSchema
  // to omit a *disabled* retryPolicy (schema requires maxAttempts >= 1, so an emitted-but-disabled
  // policy is itself invalid). Blueprint bodies (a REPEAT loop's exported <id>.blueprint.json) were
  // never normalized, so an AI Step or AI Debate step at its default (disabled) retry setting
  // inside a loop body produced a blueprint document that failed schema validation. These tests
  // build a real REPEAT loop containing both step kinds through the actual canvas → workflow →
  // zip pipeline and validate the emitted blueprint against the production blueprint.schema.json.

  function repeatNode(id: string, bid: string, bodyNodeIds: string[]): CanvasNode {
    return {
      id,
      backendStepId: bid,
      kind: 'REPEAT',
      position: { x: 0, y: 200 },
      data: {
        name: 'Loop',
        strategy: 'FIXED_COUNT',
        maxIterations: 3,
        maxIterationsAction: 'AWAIT_USER',
        bodyNodeIds,
      },
    };
  }

  function aiStepInBody(id: string, bid: string, parentId: string, maxRetries: number): CanvasNode {
    return {
      id,
      backendStepId: bid,
      kind: 'AI_STEP',
      parentNode: parentId,
      position: { x: 0, y: 0 },
      data: {
        name: 'Loop Step',
        agentRef: 'loop-agent',
        instructions: 'Iterate',
        transition: 'AUTO',
        maxRetries,
      },
    };
  }

  function aiDebateInBody(id: string, bid: string, parentId: string): CanvasNode {
    return {
      id,
      backendStepId: bid,
      kind: 'AI_DEBATE',
      parentNode: parentId,
      position: { x: 0, y: 0 },
      data: {
        name: 'Loop Debate',
        primaryAgentRef: 'agent-a',
        challengerAgentRef: 'agent-b',
        maxRounds: 2,
        resolutionPrompt: 'Resolve',
        transition: 'AUTO',
      },
    };
  }

  function repeatLoopModel(stepMaxRetries: number): CanvasModel {
    const repeat = repeatNode('repeat-1', newStepId('repeat'), ['step-a', 'debate-a']);
    const step = aiStepInBody('step-a', newStepId('ai-step'), 'repeat-1', stepMaxRetries);
    const debate = aiDebateInBody('debate-a', newStepId('ai-debate'), 'repeat-1');
    return {
      workflowId: 'loop-demo',
      workflowName: 'Loop Demo',
      description: '',
      startNodeId: 'repeat-1',
      nodes: [repeat, step, debate],
      edges: [],
      artifacts: {},
      blueprints: {},
    };
  }

  async function exportedBlueprintDoc(workflow: WorkflowDefinition): Promise<Record<string, unknown>> {
    const blob = await buildWorkflowZipBlob(workflow);
    const zip = await JSZip.loadAsync(blob);
    const blueprintEntry = Object.keys(zip.files).find((name) => name.endsWith('.blueprint.json'));
    expect(blueprintEntry).toBeDefined();
    const text = await zip.file(blueprintEntry!)!.async('text');
    return JSON.parse(text) as Record<string, unknown>;
  }

  function stepByType(blueprintDoc: Record<string, unknown>, type: string): Record<string, unknown> {
    const steps = blueprintDoc.steps as Array<Record<string, unknown>>;
    const step = steps.find((s) => (s.behaviour as Record<string, unknown> | undefined)?.type === type);
    expect(step).toBeDefined();
    return step!;
  }

  it('a REPEAT loop with an AI Step at default (disabled) retry and an AI Debate exports a schema-valid blueprint', async () => {
    const workflow = canvasToWorkflow(repeatLoopModel(0));
    const blueprintDoc = await exportedBlueprintDoc(workflow);

    const result = validateBlueprintDocument(blueprintDoc);
    expect(result.errors).toEqual([]);
    expect(result.valid).toBe(true);

    const agentStep = stepByType(blueprintDoc, 'AGENT');
    expect((agentStep.behaviour as Record<string, unknown>).retryPolicy).toBeUndefined();

    const sparStep = stepByType(blueprintDoc, 'SPAR');
    expect((sparStep.behaviour as Record<string, unknown>).retryPolicy).toBeUndefined();
  });

  it('a REPEAT loop with an AI Step at an enabled retry setting exports a schema-valid blueprint', async () => {
    const workflow = canvasToWorkflow(repeatLoopModel(3));
    const blueprintDoc = await exportedBlueprintDoc(workflow);

    const result = validateBlueprintDocument(blueprintDoc);
    expect(result.errors).toEqual([]);
    expect(result.valid).toBe(true);

    const agentStep = stepByType(blueprintDoc, 'AGENT');
    expect((agentStep.behaviour as Record<string, unknown>).retryPolicy).toEqual({
      allowRetry: true,
      allowRetryFromPrevious: false,
      maxAttempts: 3,
    });
  });

  it('validateBlueprintDocument rejects the pre-fix shape (a disabled retryPolicy left on the exported step)', () => {
    // Negative control proving the schema check above is real: this is exactly what
    // buildBlueprintJsonForRepeat produced before blueprint bodies were normalized — the disabled
    // policy's maxAttempts: 0 violates the RetryPolicy $def's minimum: 1.
    const unNormalizedBlueprint = {
      kind: 'BLUEPRINT',
      blueprintId: 'repeat-1',
      name: 'Loop',
      behaviour: {
        transition: 'AUTO',
        loopConfig: { terminationStrategy: 'FIXED_COUNT', maxIterations: 3, maxIterationsAction: 'AWAIT_USER' },
      },
      steps: [
        {
          kind: 'STEP',
          stepId: 'step-a',
          name: 'Loop Step',
          behaviour: {
            type: 'AGENT',
            agentRef: 'loop-agent',
            transition: 'AUTO',
            retryPolicy: { allowRetry: false, allowRetryFromPrevious: false, maxAttempts: 0 },
          },
          contextMapping: { inputKeys: [], outputKeys: [] },
        },
      ],
    };

    const result = validateBlueprintDocument(unNormalizedBlueprint);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.message.includes('>= 1'))).toBe(true);
  });
});
