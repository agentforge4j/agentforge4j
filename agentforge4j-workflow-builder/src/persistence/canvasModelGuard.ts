// SPDX-License-Identifier: Apache-2.0

import type { EditableArtifactItem } from '../api/types';
import type { ArtifactItemDraft, CanvasModel, DecisionCaseDraft, NodeDataByKind } from '../model/canvasModel';
import type { NodeKind } from '../model/nodeKinds';
import { NODE_KIND_META } from '../model/nodeKinds';
import { canvasToWorkflow } from '../model/mapper';

const KNOWN_NODE_KINDS = new Set(Object.keys(NODE_KIND_META));

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStringArray(value: unknown): boolean {
  return Array.isArray(value) && value.every((entry) => typeof entry === 'string');
}

/**
 * Runtime shape requirement for one field of a restored draft. `optional*` specs accept
 * `undefined` (absent) but reject a present value of the wrong type — production code
 * optional-chains those fields, and optional chaining only guards absence, not type
 * (`(5)?.trim()` still throws).
 */
type FieldSpec =
  | 'string'
  | 'number'
  | 'optionalString'
  | 'optionalBoolean'
  | 'stringArray'
  | 'optionalStringArray'
  | 'artifactItems'
  | 'decisionCases';

/**
 * Field requirements for `ASK_USER.artifactItems` entries (`ArtifactItemDraft` — and only
 * that type; the items of `CanvasModel.artifacts` values are `EditableArtifactItem`, a
 * *different* type validated by {@link EDITABLE_ARTIFACT_ITEM_SPECS} below): `id`/`label`
 * are `.trim()`ed and `type` is compared by the editor validator (`validateWorkflow.ts`),
 * `options` elements are `.trim()`ed by `hasOptions`. `key` is required —
 * `ArtifactItemDraft.key` is a required TypeScript field and `StepConfigPanel`'s inspector
 * input renders it as a controlled value (`value={item.key}`) with no fallback;
 * `artifactFromAskUser`'s `it.key?.trim()` optional chain only guards a missing draft
 * *field*, not the controlled-input consumer, so treating `key` as optional here would let
 * a restored draft desync React's controlled/uncontrolled input state.
 */
const ARTIFACT_ITEM_DRAFT_SPECS = {
  id: 'string',
  type: 'string',
  label: 'string',
  key: 'string',
  required: 'optionalBoolean',
  options: 'optionalStringArray',
} as const satisfies { [F in keyof Required<ArtifactItemDraft>]: FieldSpec };

/**
 * Field requirements for the items of `CanvasModel.artifacts` values
 * (`EditableArtifactItem`). Deliberately a separate table from
 * {@link ARTIFACT_ITEM_DRAFT_SPECS}: `EditableArtifactItem` has **no `key` field** —
 * the builder's own import path (`workflowToCanvas`, which copies `workflow.artifacts`
 * verbatim) legitimately produces items without one, so requiring `key` here would
 * reject (and, for the built-in adapter, destroy) every saved draft of an imported
 * workflow. Each table is `satisfies`-tied to its concrete type so field drift on either
 * type fails compilation independently.
 */
const EDITABLE_ARTIFACT_ITEM_SPECS = {
  id: 'string',
  type: 'string',
  label: 'string',
  required: 'optionalBoolean',
  options: 'optionalStringArray',
} as const satisfies { [F in keyof Required<EditableArtifactItem>]: FieldSpec };

/** `value`/`targetNodeId` are `.trim()`ed by the mapper, `label` is rendered by `DecisionNode`. */
const DECISION_CASE_SPECS = {
  label: 'string',
  value: 'string',
  targetNodeId: 'string',
} as const satisfies { [F in keyof Required<DecisionCaseDraft>]: FieldSpec };

/**
 * Per-kind field requirements for `node.data`, mirroring `NodeDataByKind`. The `satisfies`
 * clause ties this table to the model types at compile time: adding, removing, or renaming a
 * field on any node-data type breaks this file's build until the spec here is updated — the
 * guard can no longer silently drift behind the model the way the earlier container-only
 * checks did.
 *
 * Required vs optional follows what production code actually tolerates, not only the
 * TypeScript optionality: everything the render/mapping/validation pipeline dereferences
 * unconditionally (see the doc on {@link isRestorableCanvasModel}) is required; fields it
 * only reads behind `?.`/fallbacks (`forEachKey`, `evaluatorAgentId`, item `required`,
 * `options`) are optional but still type-checked when present.
 */
const NODE_DATA_SPECS = {
  ASK_USER: { name: 'string', question: 'string', artifactItems: 'artifactItems' },
  AI_STEP: { name: 'string', agentRef: 'string', instructions: 'string', transition: 'string', maxRetries: 'number' },
  AI_DEBATE: {
    name: 'string',
    primaryAgentRef: 'string',
    challengerAgentRef: 'string',
    maxRounds: 'number',
    resolutionPrompt: 'string',
    transition: 'string',
  },
  DECISION: { name: 'string', contextKey: 'string', cases: 'decisionCases', defaultTargetNodeId: 'string' },
  REPEAT: {
    name: 'string',
    strategy: 'string',
    maxIterations: 'number',
    forEachKey: 'optionalString',
    evaluatorAgentId: 'optionalString',
    maxIterationsAction: 'string',
    bodyNodeIds: 'stringArray',
  },
  SAVE_RESULT: { name: 'string', resultName: 'string' },
  REUSE_WORKFLOW: { name: 'string', workflowRef: 'string', transition: 'string' },
  LOAD_RESOURCE: { name: 'string', resourcePath: 'string', resultName: 'string', transition: 'string' },
  STOP: { name: 'string', reason: 'string' },
  RETRY: {
    name: 'string',
    targetNodeId: 'string',
    maxAttempts: 'number',
    fallbackTargetNodeId: 'string',
    retryMode: 'string',
  },
} as const satisfies { [K in NodeKind]: { [F in keyof Required<NodeDataByKind[K]>]: FieldSpec } };

function matchesSpec(value: unknown, spec: FieldSpec): boolean {
  switch (spec) {
    case 'string':
      return typeof value === 'string';
    case 'number':
      return typeof value === 'number' && Number.isFinite(value);
    case 'optionalString':
      return value === undefined || typeof value === 'string';
    case 'optionalBoolean':
      return value === undefined || typeof value === 'boolean';
    case 'stringArray':
      return isStringArray(value);
    case 'optionalStringArray':
      return value === undefined || isStringArray(value);
    case 'artifactItems':
      return Array.isArray(value) && value.every(isRestorableArtifactItemDraft);
    case 'decisionCases':
      return Array.isArray(value) && value.every(isRestorableDecisionCase);
  }
}

function matchesSpecs(value: Record<string, unknown>, specs: Record<string, FieldSpec>): boolean {
  return Object.entries(specs).every(([field, spec]) => matchesSpec(value[field], spec));
}

function isRestorableArtifactItemDraft(item: unknown): boolean {
  return isPlainObject(item) && matchesSpecs(item, ARTIFACT_ITEM_DRAFT_SPECS);
}

function isRestorableEditableArtifactItem(item: unknown): boolean {
  return isPlainObject(item) && matchesSpecs(item, EDITABLE_ARTIFACT_ITEM_SPECS);
}

function isRestorableDecisionCase(entry: unknown): boolean {
  return isPlainObject(entry) && matchesSpecs(entry, DECISION_CASE_SPECS);
}

/**
 * An `artifacts` map value (`EditableArtifact`): `id` and every item are dereferenced by
 * the editor validator. Items are `EditableArtifactItem` (no `key`), not
 * `ArtifactItemDraft` — see {@link EDITABLE_ARTIFACT_ITEM_SPECS}.
 */
function isRestorableArtifact(value: unknown): boolean {
  return (
    isPlainObject(value) &&
    typeof value.id === 'string' &&
    Array.isArray(value.items) &&
    value.items.every(isRestorableEditableArtifactItem)
  );
}

function isRestorableNode(value: unknown): boolean {
  if (!isPlainObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string' || typeof value.kind !== 'string' || !KNOWN_NODE_KINDS.has(value.kind)) {
    return false;
  }
  if (value.backendStepId !== undefined && typeof value.backendStepId !== 'string') {
    return false;
  }
  if (value.parentNode !== undefined && typeof value.parentNode !== 'string') {
    return false;
  }
  const position = value.position;
  if (!isPlainObject(position) || !Number.isFinite(position.x) || !Number.isFinite(position.y)) {
    return false;
  }
  const data = value.data;
  if (!isPlainObject(data)) {
    return false;
  }
  return matchesSpecs(data, NODE_DATA_SPECS[value.kind as NodeKind]);
}

function isRestorableEdge(value: unknown): boolean {
  if (!isPlainObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string' || typeof value.source !== 'string' || typeof value.target !== 'string') {
    return false;
  }
  const handleOk = value.sourceHandle === undefined || value.sourceHandle === null || typeof value.sourceHandle === 'string';
  const labelOk = value.label === undefined || value.label === null || typeof value.label === 'string';
  return handleOk && labelOk;
}

/**
 * Complete fail-closed structural gate a value must pass before it may replace the live
 * canvas model.
 *
 * A draft that fails this check — a stale shape persisted by an older builder version, a
 * truncated write, or a host adapter resolving something unexpected — is discarded instead
 * of restored. Restoring it would crash the editor at render time: the restored model is
 * fed synchronously into `buildFromCanvas` (`WorkflowBuilder.tsx`, render-time `useMemo`)
 * and the draft-sync effect, whose mapping (`canvasToWorkflow`) and validation
 * (`validateWorkflowEditor`) dereference per-kind step fields, decision cases, and artifact
 * definitions unconditionally — and because the bad draft would still be stored, every
 * remount would crash again with no UI-level way out: the "Start fresh" action lives inside
 * the very tree that crashed.
 *
 * The gate therefore validates every field the running bundle dereferences, not just
 * container presence: model scalars, per-node identity/`position`/`parentNode`, the full
 * per-kind `node.data` field set ({@link NODE_DATA_SPECS}, compile-time-tied to
 * `NodeDataByKind`), decision-case and artifact-item element shapes, edge fields, artifact
 * map values, and the optional `unsupported`/`unsupportedReasons` banner fields. Fields the
 * pipeline reads only behind `?.` or fallbacks are optional but type-checked when present.
 *
 * Unknown **extra** fields remain restorable so the check does not reject drafts from a
 * *newer* builder version that only added fields; a draft *missing* anything this bundle
 * requires is rejected — fail-closed, per the persistence contract: the built-in adapter
 * self-clears such drafts, host-adapter drafts are skipped (never rendered, never cleared —
 * the host's storage belongs to the host).
 *
 * Field shape alone is not sufficient: a fully well-typed draft can still be semantically
 * unserializable — e.g. a `REPEAT` node whose loop body (via `bodyNodeIds` or a child's
 * `parentNode`) contains a `DECISION` node, which crashes `canvasToWorkflow`'s
 * `buildBlueprintJsonForRepeat` → `serializeBehaviour('BRANCH')` (its branch-target lookup
 * can never resolve an inner-scope canvas id, and an unset default target throws outright).
 * `canvasToWorkflow` runs synchronously at render time on every restore
 * (`WorkflowBuilder.tsx`'s `buildFromCanvas` `useMemo` and the unguarded draft-sync effect),
 * so the gate runs the real mapping pipeline against the candidate as a final check —
 * closing this whole class of failure (present and future) rather than enumerating each
 * unserializable shape as its own field rule.
 */
export function isRestorableCanvasModel(value: unknown): value is CanvasModel {
  if (!isPlainObject(value)) {
    return false;
  }
  if (typeof value.workflowId !== 'string' || typeof value.workflowName !== 'string' || typeof value.description !== 'string') {
    return false;
  }
  if (value.startNodeId !== null && typeof value.startNodeId !== 'string') {
    return false;
  }
  if (!Array.isArray(value.nodes) || !value.nodes.every(isRestorableNode)) {
    return false;
  }
  if (!Array.isArray(value.edges) || !value.edges.every(isRestorableEdge)) {
    return false;
  }
  if (!isPlainObject(value.artifacts) || !Object.values(value.artifacts).every(isRestorableArtifact)) {
    return false;
  }
  if (!isPlainObject(value.blueprints) || !Object.values(value.blueprints).every(isPlainObject)) {
    return false;
  }
  if (value.unsupported !== undefined && typeof value.unsupported !== 'boolean') {
    return false;
  }
  if (value.unsupportedReasons !== undefined && !isStringArray(value.unsupportedReasons)) {
    return false;
  }
  try {
    canvasToWorkflow(value as CanvasModel);
  } catch {
    return false;
  }
  return true;
}
