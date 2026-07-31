// SPDX-License-Identifier: Apache-2.0

import JSZip from 'jszip';
import { describe, expect, it } from 'vitest';
import type { CanvasModel, CanvasNode, NodeData } from '../src/model/canvasModel';
import type { NodeKind } from '../src/model/nodeKinds';
import { NODE_KIND_META } from '../src/model/nodeKinds';
import { importWorkflowZip } from '../src/io/browser/zip';
import { WorkflowParseError } from '../src/io/core';
import {
  canvasToWorkflow,
  defaultNodeData,
  exportStepJson,
  workflowDetailToCanvas,
  workflowToCanvas,
} from '../src/model/mapper';
import {
  BUILDER_OWNED_DETAIL_STEP_KEYS,
  BUILDER_OWNED_STEP_KEYS,
  applyPreservedStepFields,
  collectPreservedStepFields,
} from '../src/model/preservedStepFields';
import { isRestorableCanvasModel } from '../src/persistence/canvasModelGuard';
import type { EditableStep, WorkflowDefinition } from '../src/api/types';

const KNOWN_TIERS = ['LITE', 'STANDARD', 'POWERFUL', 'PREMIUM'] as const;

function emptyModel(nodes: CanvasNode[]): CanvasModel {
  return {
    workflowId: 'wf',
    workflowName: 'Workflow',
    description: '',
    startNodeId: nodes[0]?.id ?? null,
    nodes,
    edges: [],
    artifacts: {},
    blueprints: {},
  };
}

/** Every step-level property of the framework's StepDefinition that the builder does not model. */
const UNMODELLED_STEP_FIELDS = {
  modelTier: 'PREMIUM',
  estimatedInputTokens: 1200,
  estimatedOutputTokens: 400,
  maxUserPromptRounds: 3,
} as const;

function runtimeStep(extra: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    kind: 'STEP',
    stepId: 'step-a',
    name: 'Do the work',
    contextMapping: { inputKeys: [], outputKeys: [] },
    behaviour: { type: 'AGENT', agentRef: 'agent-1', transition: 'AUTO' },
    ...extra,
  };
}

function runtimeDocument(stepExtra: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    kind: 'WORKFLOW',
    schemaVersion: 1,
    id: 'round-trip',
    name: 'Round trip',
    description: '',
    steps: [runtimeStep(stepExtra)],
  };
}

/** Builds a real `.workflow.zip` from a runtime document so the import path under test is the real one. */
async function zipOf(document: Record<string, unknown>): Promise<File> {
  const zip = new JSZip();
  const folder = zip.folder(`${String(document.id)}.workflow`);
  folder?.file('workflow.json', JSON.stringify(document, null, 2));
  const blob = await zip.generateAsync({ type: 'blob' });
  return new File([blob], `${String(document.id)}.workflow.zip`, { type: 'application/zip' });
}

/** Import a document and export the one step straight back out, with no edit in between. */
async function roundTrip(stepExtra: Record<string, unknown>): Promise<Record<string, unknown>> {
  const imported = await importWorkflowZip(await zipOf(runtimeDocument(stepExtra)));
  const step = imported.steps[0] as EditableStep;
  return exportStepJson(imported, step);
}

describe('preserved step fields — import/export round trip', () => {
  it.each(KNOWN_TIERS)('preserves modelTier %s exactly', async (tier) => {
    const exported = await roundTrip({ modelTier: tier });
    expect(exported.modelTier).toBe(tier);
  });

  it('rejects an unknown future tier at import rather than silently deleting or downgrading it', async () => {
    // The documented handling for an unknown tier: the import path validates the document against
    // the framework's workflow schema, whose modelTier enum is closed, so the whole import fails
    // visibly. Nothing is silently dropped or rewritten — which is the outcome that matters. If the
    // schema ever opens that enum, the passthrough carries the value through untouched, because it
    // stores values without interpreting them (proven by the arbitrary-key test below).
    await expect(importWorkflowZip(await zipOf(runtimeDocument({ modelTier: 'TRANSCENDENT' })))).rejects.toThrow(
      WorkflowParseError,
    );
  });

  it('carries a value it has no knowledge of, so a future field survives untouched', () => {
    // The mechanism itself never interprets what it holds; only the schema decides legality.
    const preserved = collectPreservedStepFields(runtimeStep({ someFutureField: { nested: ['x'] } }));
    expect(preserved).toEqual({ someFutureField: { nested: ['x'] } });
    expect(applyPreservedStepFields({ kind: 'STEP' }, preserved).someFutureField).toEqual({ nested: ['x'] });
  });

  it('preserves every unmodelled StepDefinition property, not only modelTier', async () => {
    const exported = await roundTrip(UNMODELLED_STEP_FIELDS);
    for (const [key, value] of Object.entries(UNMODELLED_STEP_FIELDS)) {
      expect(exported[key]).toEqual(value);
    }
  });

  it('leaves an absent modelTier absent rather than defaulting one into the document', async () => {
    const exported = await roundTrip({});
    expect('modelTier' in exported).toBe(false);
    expect(exported.preservedFields).toBeUndefined();
  });

  it('keeps a step-level override step-level — it never migrates onto the workflow root', async () => {
    const imported = await importWorkflowZip(await zipOf(runtimeDocument({ modelTier: 'POWERFUL' })));
    expect((imported as unknown as Record<string, unknown>).modelTier).toBeUndefined();
    expect(imported.steps[0]?.preservedFields).toEqual({ modelTier: 'POWERFUL' });
  });

  it('loses no unrelated field across a whole-document round trip', async () => {
    const exported = await roundTrip(UNMODELLED_STEP_FIELDS);
    expect(exported.kind).toBe('STEP');
    expect(exported.stepId).toBe('step-a');
    expect(exported.name).toBe('Do the work');
    expect(exported.behaviour).toMatchObject({ type: 'AGENT', agentRef: 'agent-1', transition: 'AUTO' });
    expect(exported.contextMapping).toEqual({ inputKeys: [], outputKeys: [] });
  });
});

describe('preserved step fields — builder-owned fields win', () => {
  it('never captures a field the builder models', () => {
    const preserved = collectPreservedStepFields(runtimeStep(UNMODELLED_STEP_FIELDS));
    for (const owned of BUILDER_OWNED_STEP_KEYS) {
      expect(preserved).not.toHaveProperty(owned);
    }
  });

  it('lets the builder-written body overwrite a stale preserved value of the same key', () => {
    const merged = applyPreservedStepFields({ name: 'Edited' }, { name: 'Stale', modelTier: 'LITE' });
    expect(merged.name).toBe('Edited');
    expect(merged.modelTier).toBe('LITE');
  });

  it('mutates neither argument', () => {
    const body = { name: 'Edited' };
    const preserved = Object.freeze({ modelTier: 'LITE' });
    applyPreservedStepFields(body, preserved);
    expect(body).toEqual({ name: 'Edited' });
    expect(preserved).toEqual({ modelTier: 'LITE' });
  });

  it('survives an edit to the step the builder does own', async () => {
    const imported = await importWorkflowZip(await zipOf(runtimeDocument({ modelTier: 'PREMIUM' })));
    const edited: EditableStep = { ...(imported.steps[0] as EditableStep), name: 'Renamed by the user' };
    const exported = exportStepJson({ ...imported, steps: [edited] }, edited);
    expect(exported.name).toBe('Renamed by the user');
    expect(exported.modelTier).toBe('PREMIUM');
  });
});

describe('preserved step fields — the second import path (host workflow detail DTO)', () => {
  it('preserves an unmodelled field arriving through workflowDetailToCanvas', () => {
    const model = workflowDetailToCanvas({
      id: 'detail',
      name: 'Detail',
      steps: [
        {
          kind: 'STEP',
          step: {
            stepId: 'step-a',
            name: 'Do the work',
            behaviour: { type: 'AGENT', agentRef: 'agent-1', transition: 'AUTO' },
            ...UNMODELLED_STEP_FIELDS,
          } as never,
        },
      ],
    });
    expect(model.nodes[0]?.preservedFields).toEqual(UNMODELLED_STEP_FIELDS);
  });

  it('never carries the DTO envelope shape into an exported step', () => {
    // inputKeys/outputKeys are the DTO's own flattening of contextMapping; emitting them onto a
    // runtime step would produce a document the framework schema rejects (additionalProperties:false).
    const preserved = collectPreservedStepFields(
      {
        stepId: 's',
        name: 'n',
        behaviour: {},
        inputKeys: ['a'],
        outputKeys: ['b'],
        blueprintRef: { blueprintId: 'bp' },
        nestedWorkflow: {},
        modelTier: 'LITE',
      },
      BUILDER_OWNED_DETAIL_STEP_KEYS,
    );
    expect(preserved).toEqual({ modelTier: 'LITE' });
  });
});

describe('preserved step fields — every node kind', () => {
  const KINDS = Object.keys(NODE_KIND_META) as NodeKind[];

  it('covers all ten node kinds', () => {
    expect(KINDS).toHaveLength(10);
  });

  // The projection that used to drop these fields is per-kind, so a fix proved on one kind says
  // nothing about the other nine.
  it.each(KINDS)('carries preserved fields across the canvas hops for %s', (kind) => {
    const node = {
      id: 'n-0',
      backendStepId: 'step-a',
      kind,
      position: { x: 0, y: 0 },
      data: defaultNodeData(kind) as NodeData,
      preservedFields: { modelTier: 'PREMIUM' },
    } as CanvasNode;
    const model: CanvasModel = emptyModel([node]);

    const workflow = canvasToWorkflow(model);
    const step = workflow.steps.find((candidate) => candidate.stepId === 'step-a');
    if (!step) {
      // SAVE_RESULT folds into the previous step's context mapping and REPEAT becomes a blueprint
      // body, so neither yields a step of its own. Assert that rather than passing silently.
      expect(['SAVE_RESULT', 'REPEAT']).toContain(kind);
      return;
    }

    expect(step.preservedFields).toEqual({ modelTier: 'PREMIUM' });

    if (kind === 'DECISION') {
      // A DECISION serializes its branch targets inline, so a single-node model cannot be exported
      // at all ("Default branch target missing"). The canvas hop above is what this case proves;
      // the export half is covered by the round-trip suite, which exports a real AGENT step.
      return;
    }
    expect(exportStepJson(workflow as WorkflowDefinition, step).modelTier).toBe('PREMIUM');
  });

  it('survives the canvas round trip in both directions', async () => {
    const imported = await importWorkflowZip(await zipOf(runtimeDocument({ modelTier: 'STANDARD' })));
    const model = workflowToCanvas(imported);
    expect(model.nodes[0]?.preservedFields).toEqual({ modelTier: 'STANDARD' });

    const back = canvasToWorkflow(model);
    expect(back.steps[0]?.preservedFields).toEqual({ modelTier: 'STANDARD' });
  });
});

describe('preserved step fields — draft persistence and history', () => {
  function draftModel(preservedFields: unknown): unknown {
    return {
      nodes: [
        {
          id: 'n-0',
          backendStepId: 'step-a',
          kind: 'AI_STEP',
          position: { x: 0, y: 0 },
          data: defaultNodeData('AI_STEP'),
          preservedFields,
        },
      ],
      edges: [],
      artifacts: {},
      blueprints: {},
      workflowId: 'wf',
      workflowName: 'Workflow',
      description: '',
      startNodeId: 'n-0',
    };
  }

  it('restores a draft carrying preserved fields', () => {
    expect(isRestorableCanvasModel(draftModel({ modelTier: 'PREMIUM' }))).toBe(true);
  });

  it('restores a draft with no preserved fields at all', () => {
    expect(isRestorableCanvasModel(draftModel(undefined))).toBe(true);
  });

  it.each([['a string', 'PREMIUM'], ['an array', ['PREMIUM']], ['null', null]])(
    'rejects a draft whose preserved fields are %s',
    (_label, value) => {
      expect(isRestorableCanvasModel(draftModel(value))).toBe(false);
    },
  );

  it('is part of the canvas model, so a whole-model history snapshot carries it', async () => {
    // Undo/redo stores whole CanvasModel snapshots (useHistoryState<CanvasModel>), so preservation
    // across undo follows from the field living on the node. Proven by snapshotting and restoring.
    const imported = await importWorkflowZip(await zipOf(runtimeDocument({ modelTier: 'POWERFUL' })));
    const before = workflowToCanvas(imported);
    const snapshot = structuredClone(before) as CanvasModel;
    const edited: CanvasModel = {
      ...before,
      nodes: before.nodes.map((node) => ({ ...node, position: { x: 99, y: 99 } })),
    };

    expect(edited.nodes[0]?.preservedFields).toEqual({ modelTier: 'POWERFUL' });
    expect(snapshot.nodes[0]?.preservedFields).toEqual({ modelTier: 'POWERFUL' });
    expect(canvasToWorkflow(snapshot).steps[0]?.preservedFields).toEqual({ modelTier: 'POWERFUL' });
  });
});
