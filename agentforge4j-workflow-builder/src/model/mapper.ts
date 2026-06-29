// SPDX-License-Identifier: Apache-2.0

import type {
  AiDebateNodeData,
  AiStepNodeData,
  AskUserNodeData,
  CanvasEdge,
  CanvasModel,
  CanvasNode,
  DecisionNodeData,
  LoadResourceNodeData,
  NodeData,
  RepeatNodeData,
  RetryNodeData,
  ReuseWorkflowNodeData,
  SaveResultNodeData,
  StopNodeData,
} from './canvasModel';
import type { NodeKind } from './nodeKinds';
import type {
  BehaviourType,
  BlueprintJsonObject,
  BranchConfig,
  EditableArtifact,
  EditableArtifactItem,
  EditableStep,
  RetryPreviousConfig,
  StepTransition,
  TopLevelScheduleEntry,
  WorkflowDefinition,
} from '../api/types';
import { NODE_LABELS } from '../copy/workflow-terminology';
import { customAlphabet } from 'nanoid';

const nanoid = customAlphabet('abcdefghijklmnopqrstuvwxyz0123456789', 10);

function findStep(workflow: WorkflowDefinition, stepId: string): EditableStep | undefined {
  const t = stepId.trim();
  return workflow.steps.find((s) => s.stepId.trim() === t);
}

function retryPolicyNone(): Record<string, unknown> {
  return {
    allowRetry: false,
    allowRetryFromPrevious: false,
    allowAgentSwap: false,
    allowPromptOverride: false,
    maxAttempts: 0,
  };
}

function retryPolicySimple(maxAttempts: number): Record<string, unknown> {
  return {
    allowRetry: true,
    allowRetryFromPrevious: false,
    allowAgentSwap: false,
    allowPromptOverride: false,
    maxAttempts,
  };
}

export function exportStepJson(workflow: WorkflowDefinition, step: EditableStep): Record<string, unknown> {
  return serializeStepExecutable(workflow, step);
}

function serializeStepExecutable(workflow: WorkflowDefinition, step: EditableStep): Record<string, unknown> {
  const cm = step.contextMapping ?? { inputKeys: [], outputKeys: [] };
  const body: Record<string, unknown> = {
    kind: 'STEP',
    stepId: step.stepId,
    name: step.name,
    behaviour: serializeBehaviour(workflow, step),
    contextMapping: {
      inputKeys: cm.inputKeys,
      outputKeys: cm.outputKeys,
    },
  };
  if (step.stepPrompt?.trim()) {
    body.stepPrompt = step.stepId;
  }
  return body;
}

function serializeBehaviour(workflow: WorkflowDefinition, step: EditableStep): Record<string, unknown> {
  switch (step.behaviourType) {
    case 'INPUT': {
      const config = step.config as { artifactId: string; transition: string };
      return {
        type: 'INPUT',
        artifactId: config.artifactId,
        transition: config.transition,
      };
    }
    case 'AGENT': {
      const config = step.config as { agentRef: string; transition: string; maxRetries?: number };
      const max = config.maxRetries ?? 0;
      return {
        type: 'AGENT',
        agentRef: config.agentRef,
        transition: config.transition,
        retryPolicy: max > 0 ? retryPolicySimple(max) : retryPolicyNone(),
      };
    }
    case 'SPAR': {
      const config = step.config as {
        agentRef: string;
        challengerAgentId: string;
        maxRounds: number;
        resolutionPrompt: string;
        transition: string;
      };
      return {
        type: 'SPAR',
        agentRef: config.agentRef,
        transition: config.transition,
        retryPolicy: retryPolicyNone(),
        sparConfig: {
          challengerAgentId: config.challengerAgentId,
          maxRounds: config.maxRounds,
          resolutionPrompt: config.resolutionPrompt,
        },
      };
    }
    case 'BRANCH': {
      const config = step.config as BranchConfig;
      const branches: Record<string, unknown> = {};
      for (const [value, targetId] of Object.entries(config.branches)) {
        const target = findStep(workflow, targetId);
        if (!target) {
          throw new Error(`Branch target missing: ${targetId}`);
        }
        branches[value] = serializeStepExecutable(workflow, target);
      }
      const def = findStep(workflow, config.defaultBranch);
      if (!def) {
        throw new Error(`Default branch target missing: ${config.defaultBranch}`);
      }
      return {
        type: 'BRANCH',
        contextKey: config.contextKey,
        branches,
        defaultBranch: serializeStepExecutable(workflow, def),
      };
    }
    case 'WORKFLOW_BEHAVIOUR': {
      const config = step.config as { workflowRef: string; transition: string };
      return {
        type: 'WORKFLOW',
        workflowRef: config.workflowRef,
        transition: config.transition,
      };
    }
    case 'RESOURCE': {
      const config = step.config as { resourcePath: string; contextKey: string; transition: string };
      return {
        type: 'RESOURCE',
        resourcePath: config.resourcePath,
        contextKey: config.contextKey,
        transition: config.transition,
      };
    }
    case 'FAIL': {
      const config = step.config as { reason: string };
      return { type: 'FAIL', reason: config.reason };
    }
    case 'RETRY_PREVIOUS': {
      const config = step.config as RetryPreviousConfig;
      const fallbackStep = config.fallback.trim() ? findStep(workflow, config.fallback) : undefined;
      const fallback =
        fallbackStep != null
          ? serializeStepExecutable(workflow, fallbackStep)
          : serializeStepExecutable(workflow, {
              stepId: `${step.stepId}-fallback-missing`,
              name: 'Missing fallback',
              behaviourType: 'FAIL',
              config: { reason: 'Retry fallback step was not configured.' },
            });
      return {
        type: 'RETRY_PREVIOUS',
        retryStepId: config.retryStepId,
        retryMode: config.retryMode,
        maxAttempts: config.maxAttempts,
        fallback,
      };
    }
  }
}

export type BundleWorkflowDefinition = WorkflowDefinition;

export function newStepId(prefix: string): string {
  return `${prefix}-${nanoid()}`;
}

export function defaultNodeData(kind: NodeKind): NodeData {
  switch (kind) {
    case 'ASK_USER':
      return {
        name: '',
        question: '',
        artifactItems: [
          { id: newStepId('item'), type: 'TEXT', label: 'Response', key: 'value', required: true },
        ],
      };
    case 'AI_STEP':
      return {
        name: '',
        agentRef: '',
        instructions: '',
        transition: 'AUTO',
        maxRetries: 0,
      };
    case 'AI_DEBATE':
      return {
        name: '',
        primaryAgentRef: '',
        challengerAgentRef: '',
        maxRounds: 2,
        resolutionPrompt: '',
        transition: 'AUTO',
      };
    case 'DECISION':
      return {
        name: '',
        contextKey: '',
        cases: [{ label: 'Yes', value: 'yes', targetNodeId: '' }],
        defaultTargetNodeId: '',
      };
    case 'REPEAT':
      return {
        name: '',
        strategy: 'FIXED_COUNT',
        maxIterations: 3,
        maxIterationsAction: 'AWAIT_USER',
        bodyNodeIds: [],
      };
    case 'SAVE_RESULT':
      return { name: '', resultName: '' };
    case 'REUSE_WORKFLOW':
      return { name: '', workflowRef: '', transition: 'AUTO' };
    case 'LOAD_RESOURCE':
      return { name: '', resourcePath: '/schema/', resultName: '', transition: 'AUTO' };
    case 'STOP':
      return { name: '', reason: '' };
    case 'RETRY':
      return {
        name: '',
        targetNodeId: '',
        maxAttempts: 3,
        fallbackTargetNodeId: '',
        retryMode: 'SINGLE_STEP',
      };
  }
}

function nodeById(model: CanvasModel): Map<string, CanvasNode> {
  return new Map(model.nodes.map((n) => [n.id, n]));
}

function outgoing(edges: CanvasEdge[], source: string, sourceHandle?: string | null): CanvasEdge[] {
  return edges.filter((e) => e.source === source && (sourceHandle == null ? true : e.sourceHandle === sourceHandle));
}

/** Nodes inside a repeat body (by parentNode chain). */
function loopBodyNodeIds(model: CanvasModel): Set<string> {
  const ids = new Set<string>();
  for (const n of model.nodes) {
    if (n.parentNode) {
      const parent = model.nodes.find((p) => p.id === n.parentNode);
      if (parent?.kind === 'REPEAT') {
        ids.add(n.id);
      }
    }
  }
  return ids;
}

export function collectBranchNestedIds(model: CanvasModel): Set<string> {
  const nested = new Set<string>();
  for (const n of model.nodes) {
    if (n.kind !== 'DECISION') {
      continue;
    }
    const d = n.data as DecisionNodeData;
    const mergeId = d.defaultTargetNodeId?.trim();
    if (!mergeId) {
      continue;
    }
    const visit = (start: string) => {
      const q = [start];
      const seen = new Set<string>();
      while (q.length) {
        const cur = q.pop()!;
        if (cur === mergeId || seen.has(cur)) {
          continue;
        }
        seen.add(cur);
        nested.add(cur);
        for (const e of outgoing(model.edges, cur)) {
          q.push(e.target);
        }
      }
    };
    for (const c of d.cases) {
      if (c.targetNodeId.trim()) {
        visit(c.targetNodeId.trim());
      }
    }
  }
  return nested;
}

function topoMainNodeOrder(model: CanvasModel, skip: Set<string>): string[] {
  const start = model.startNodeId;
  if (!start || skip.has(start)) {
    return [];
  }
  const nodes = model.nodes.filter((n) => !skip.has(n.id) && !n.parentNode);
  const ids = new Set(nodes.map((n) => n.id));
  const indeg = new Map<string, number>();
  for (const id of ids) {
    indeg.set(id, 0);
  }
  const adj = new Map<string, string[]>();
  for (const id of ids) {
    adj.set(id, []);
  }
  for (const e of model.edges) {
    if (!ids.has(e.source) || !ids.has(e.target)) {
      continue;
    }
    indeg.set(e.target, (indeg.get(e.target) ?? 0) + 1);
    adj.get(e.source)!.push(e.target);
  }
  const q: string[] = [];
  for (const [id, d] of indeg) {
    if (d === 0) {
      q.push(id);
    }
  }
  if (!q.includes(start)) {
    q.unshift(start);
  }
  const out: string[] = [];
  const seen = new Set<string>();
  while (q.length) {
    const cur = q.shift()!;
    if (seen.has(cur)) {
      continue;
    }
    seen.add(cur);
    out.push(cur);
    for (const nxt of adj.get(cur) ?? []) {
      const next = (indeg.get(nxt) ?? 0) - 1;
      indeg.set(nxt, next);
      if (next === 0) {
        q.push(nxt);
      }
    }
  }
  for (const id of ids) {
    if (!seen.has(id)) {
      out.push(id);
    }
  }
  return out;
}

function artifactFromAskUser(node: CanvasNode, artifactId: string): EditableArtifact {
  const d = node.data as AskUserNodeData;
  const items: EditableArtifactItem[] = (d.artifactItems ?? []).map((it) => ({
    id: it.key?.trim() || it.id,
    type: it.type,
    label: it.label,
    required: it.required,
    options: it.options,
  }));
  return { id: artifactId, items };
}

function canvasNodeToStep(
  node: CanvasNode,
  ctx: { artifactIdForAsk?: string; stepId: string },
): EditableStep {
  const base = (name: string): EditableStep => ({
    stepId: ctx.stepId,
    name: name || 'Step',
    behaviourType: 'INPUT',
    config: { artifactId: '', transition: 'AUTO' },
    stepPrompt: '',
    contextMapping: { inputKeys: [], outputKeys: [] },
  });

  switch (node.kind) {
    case 'ASK_USER': {
      const d = node.data as AskUserNodeData;
      const artifactId = ctx.artifactIdForAsk ?? node.id;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.ASK_USER,
        behaviourType: 'INPUT',
        config: { artifactId, transition: 'AUTO' },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'AI_STEP': {
      const d = node.data as AiStepNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.AI_STEP,
        behaviourType: 'AGENT',
        config: {
          agentRef: d.agentRef,
          transition: d.transition,
          maxRetries: d.maxRetries,
        },
        stepPrompt: d.instructions,
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'AI_DEBATE': {
      const d = node.data as AiDebateNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.AI_DEBATE,
        behaviourType: 'SPAR',
        config: {
          agentRef: d.primaryAgentRef,
          challengerAgentId: d.challengerAgentRef,
          maxRounds: d.maxRounds,
          resolutionPrompt: d.resolutionPrompt,
          transition: d.transition,
        },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'DECISION': {
      const d = node.data as DecisionNodeData;
      const branches: Record<string, string> = {};
      for (const c of d.cases) {
        if (c.value.trim() && c.targetNodeId.trim()) {
          branches[c.value.trim()] = c.targetNodeId.trim();
        }
      }
      return {
        stepId: ctx.stepId,
        name: d.name || 'Decision',
        behaviourType: 'BRANCH',
        config: {
          contextKey: d.contextKey,
          branches,
          defaultBranch: d.defaultTargetNodeId.trim(),
        },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'REUSE_WORKFLOW': {
      const d = node.data as ReuseWorkflowNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.REUSE_WORKFLOW,
        behaviourType: 'WORKFLOW_BEHAVIOUR',
        config: { workflowRef: d.workflowRef, transition: d.transition },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'LOAD_RESOURCE': {
      const d = node.data as LoadResourceNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.LOAD_RESOURCE,
        behaviourType: 'RESOURCE',
        config: {
          resourcePath: d.resourcePath,
          contextKey: d.resultName,
          transition: d.transition,
        },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'STOP': {
      const d = node.data as StopNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.STOP,
        behaviourType: 'FAIL',
        config: { reason: d.reason },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'RETRY': {
      const d = node.data as RetryNodeData;
      return {
        stepId: ctx.stepId,
        name: d.name || NODE_LABELS.RETRY,
        behaviourType: 'RETRY_PREVIOUS',
        config: {
          retryStepId: d.targetNodeId,
          retryMode: d.retryMode,
          maxAttempts: d.maxAttempts,
          fallback: d.fallbackTargetNodeId,
        },
        stepPrompt: '',
        contextMapping: { inputKeys: [], outputKeys: [] },
      };
    }
    case 'SAVE_RESULT':
    case 'REPEAT':
      return base(node.data.name as string);
    default:
      return base('Step');
  }
}

/** Read-only wire behaviour type for inspector display (uses `canvasNodeToStep` mapping). */
export function wireBehaviourTypeForCanvasNode(node: CanvasNode): BehaviourType | null {
  if (node.kind === 'SAVE_RESULT' || node.kind === 'REPEAT') {
    return null;
  }
  return canvasNodeToStep(node, {
    stepId: node.backendStepId ?? node.id,
    artifactIdForAsk: node.id,
  }).behaviourType;
}

/** Map canvas node id → backend step id (stable for session). */
function buildCanvasToStepIdMap(model: CanvasModel): Map<string, string> {
  const map = new Map<string, string>();
  const prefixFor: Record<NodeKind, string> = {
    ASK_USER: 'ask-user',
    AI_STEP: 'ai-step',
    AI_DEBATE: 'ai-debate',
    DECISION: 'decision',
    REPEAT: 'repeat',
    SAVE_RESULT: 'save-result',
    REUSE_WORKFLOW: 'reuse-wf',
    LOAD_RESOURCE: 'load-res',
    STOP: 'stop',
    RETRY: 'retry',
  };
  for (const n of model.nodes) {
    map.set(n.id, n.backendStepId ?? newStepId(prefixFor[n.kind] ?? 'step'));
  }
  return map;
}

function buildBlueprintJsonForRepeat(
  model: CanvasModel,
  repeatNode: CanvasNode,
  canvasToStepId: Map<string, string>,
  artifacts: Record<string, EditableArtifact>,
): BlueprintJsonObject {
  const d = repeatNode.data as RepeatNodeData;
  const byId = nodeById(model);
  const ordered = (d.bodyNodeIds?.length ? d.bodyNodeIds : model.nodes.filter((n) => n.parentNode === repeatNode.id).map((n) => n.id))
    .map((id) => byId.get(id))
    .filter((n): n is CanvasNode => Boolean(n));
  const innerEditable: EditableStep[] = [];
  for (const inner of ordered) {
    const sid = canvasToStepId.get(inner.id) ?? newStepId('inner');
    let artifactIdForAsk: string | undefined;
    if (inner.kind === 'ASK_USER') {
      artifactIdForAsk = `artifact-${sid}`;
      artifacts[artifactIdForAsk] = artifactFromAskUser(inner, artifactIdForAsk);
    }
    innerEditable.push(canvasNodeToStep(inner, { stepId: sid, artifactIdForAsk }));
  }
  const wfInner: WorkflowDefinition = {
    id: 'inline',
    name: '',
    description: '',
    steps: innerEditable,
    artifacts,
  };
  const innerSteps = innerEditable.map((s) => exportStepJson(wfInner, s));
  const loopConfig: Record<string, unknown> = {
    terminationStrategy: d.strategy,
    maxIterations: d.maxIterations,
    maxIterationsAction: d.maxIterationsAction,
  };
  if (d.strategy === 'FOR_EACH' && d.forEachKey?.trim()) {
    loopConfig.forEachContextKey = d.forEachKey.trim();
  }
  if (d.strategy === 'EVALUATOR' && d.evaluatorAgentId?.trim()) {
    loopConfig.evaluatorAgentId = d.evaluatorAgentId.trim();
  }
  const blueprintId = canvasToStepId.get(repeatNode.id) ?? newStepId('repeat');
  return {
    kind: 'BLUEPRINT',
    blueprintId,
    name: d.name || 'Repeat',
    behaviour: {
      loopConfig,
      transition: 'AUTO',
    },
    steps: innerSteps,
  };
}

export function canvasToWorkflow(model: CanvasModel): BundleWorkflowDefinition {
  const canvasToStepId = buildCanvasToStepIdMap(model);
  const loopBodies = loopBodyNodeIds(model);
  const branchNested = collectBranchNestedIds(model);
  const skipForMainTopo = new Set([...loopBodies, ...branchNested]);
  const mainOrder = topoMainNodeOrder(model, skipForMainTopo);

  const artifacts: Record<string, EditableArtifact> = { ...model.artifacts };
  const steps: EditableStep[] = [];
  const blueprintBodies: Record<string, BlueprintJsonObject> = { ...model.blueprints };
  const topLevelSchedule: TopLevelScheduleEntry[] = [];

  const pushStepForNode = (node: CanvasNode) => {
    const sid = canvasToStepId.get(node.id)!;
    let artifactIdForAsk: string | undefined;
    if (node.kind === 'ASK_USER') {
      artifactIdForAsk = `artifact-${sid}`;
      artifacts[artifactIdForAsk] = artifactFromAskUser(node, artifactIdForAsk);
    }
    const st = canvasNodeToStep(node, { stepId: sid, artifactIdForAsk });
    if (node.kind === 'SAVE_RESULT') {
      const prev = steps[steps.length - 1];
      const d = node.data as SaveResultNodeData;
      if (prev && d.resultName.trim()) {
        prev.contextMapping = {
          inputKeys: prev.contextMapping?.inputKeys ?? [],
          outputKeys: [d.resultName.trim()],
        };
      }
      return;
    }
    steps.push(st);
  };

  for (const nid of mainOrder) {
    const node = model.nodes.find((n) => n.id === nid);
    if (!node) {
      continue;
    }
    if (node.kind === 'REPEAT') {
      const bpId = canvasToStepId.get(node.id)!;
      blueprintBodies[bpId] = buildBlueprintJsonForRepeat(model, node, canvasToStepId, artifacts);
      topLevelSchedule.push({ kind: 'BLUEPRINT_REF', blueprintId: bpId });
      continue;
    }
    if (node.kind === 'SAVE_RESULT') {
      pushStepForNode(node);
      continue;
    }
    const idx = steps.length;
    pushStepForNode(node);
    topLevelSchedule.push({ kind: 'STEP', stepIndex: idx });
  }

  for (const nid of branchNested) {
    const node = model.nodes.find((n) => n.id === nid);
    if (!node || node.kind === 'SAVE_RESULT') {
      continue;
    }
    pushStepForNode(node);
  }

  for (const st of steps) {
    if (st.behaviourType === 'BRANCH') {
      const c = st.config as BranchConfig;
      const mapBranches: Record<string, string> = {};
      for (const [k, targetCanvas] of Object.entries(c.branches)) {
        const tid = canvasToStepId.get(targetCanvas);
        mapBranches[k] = tid ?? targetCanvas;
      }
      const defTid = canvasToStepId.get(c.defaultBranch.trim()) ?? c.defaultBranch.trim();
      st.config = { ...c, branches: mapBranches, defaultBranch: defTid };
    }
    if (st.behaviourType === 'RETRY_PREVIOUS') {
      const c = st.config as RetryPreviousConfig;
      const fb = canvasToStepId.get(c.fallback.trim()) ?? c.fallback.trim();
      const rt = canvasToStepId.get(c.retryStepId.trim()) ?? c.retryStepId.trim();
      st.config = { ...c, retryStepId: rt, fallback: fb };
    }
  }

  return {
    id: model.workflowId.trim(),
    name: model.workflowName.trim(),
    description: model.description.trim(),
    steps,
    artifacts,
    blueprintBodies,
    topLevelSchedule,
  };
}

export function workflowToCanvas(workflow: WorkflowDefinition): CanvasModel {
  const nodes: CanvasNode[] = [];
  const edges: CanvasEdge[] = [];
  let y = 0;
  const gap = 120;
  const stepToNode = new Map<string, string>();
  workflow.steps.forEach((s, i) => {
    stepToNode.set(s.stepId, `n-${i}`);
  });

  for (let i = 0; i < workflow.steps.length; i++) {
    const st = workflow.steps[i];
    const cid = `n-${i}`;
    let kind: NodeKind = 'AI_STEP';
    let data: NodeData = defaultNodeData('AI_STEP');
    if (st.behaviourType === 'INPUT') {
      kind = 'ASK_USER';
      const cfg = st.config as { artifactId: string };
      const art = workflow.artifacts[cfg.artifactId];
      data = {
        name: st.name,
        question: '',
        artifactItems:
          art?.items.map((it) => ({
            id: it.id,
            type: it.type,
            label: it.label,
            key: it.id,
            required: it.required,
            options: it.options,
          })) ?? [],
      } as NodeData;
    } else if (st.behaviourType === 'AGENT') {
      const cfg = st.config as { agentRef: string; transition: string; maxRetries?: number };
      data = {
        name: st.name,
        agentRef: cfg.agentRef,
        instructions: st.stepPrompt ?? '',
        transition: cfg.transition as StepTransition,
        maxRetries: cfg.maxRetries ?? 0,
      };
    } else if (st.behaviourType === 'BRANCH') {
      kind = 'DECISION';
      const cfg = st.config as BranchConfig;
      data = {
        name: st.name,
        contextKey: cfg.contextKey,
        cases: Object.entries(cfg.branches).map(([value, targetNodeId]) => ({
          label: value,
          value,
          targetNodeId: stepToNode.get(targetNodeId) ?? '',
        })),
        defaultTargetNodeId: stepToNode.get(cfg.defaultBranch) ?? '',
      };
    } else if (st.behaviourType === 'WORKFLOW_BEHAVIOUR') {
      kind = 'REUSE_WORKFLOW';
      const cfg = st.config as { workflowRef: string; transition: string };
      data = { name: st.name, workflowRef: cfg.workflowRef, transition: cfg.transition as 'AUTO' };
    } else if (st.behaviourType === 'RESOURCE') {
      kind = 'LOAD_RESOURCE';
      const cfg = st.config as { resourcePath: string; contextKey: string; transition: string };
      data = {
        name: st.name,
        resourcePath: cfg.resourcePath,
        resultName: cfg.contextKey,
        transition: cfg.transition as 'AUTO',
      };
    } else if (st.behaviourType === 'FAIL') {
      kind = 'STOP';
      const cfg = st.config as { reason: string };
      data = { name: st.name, reason: cfg.reason };
    } else if (st.behaviourType === 'RETRY_PREVIOUS') {
      kind = 'RETRY';
      const cfg = st.config as RetryPreviousConfig;
      data = {
        name: st.name,
        targetNodeId: stepToNode.get(cfg.retryStepId) ?? cfg.retryStepId,
        maxAttempts: cfg.maxAttempts,
        fallbackTargetNodeId: stepToNode.get(cfg.fallback) ?? cfg.fallback,
        retryMode: cfg.retryMode,
      };
    } else if (st.behaviourType === 'SPAR') {
      kind = 'AI_DEBATE';
      const cfg = st.config as {
        agentRef: string;
        challengerAgentId: string;
        maxRounds: number;
        resolutionPrompt: string;
        transition: string;
      };
      data = {
        name: st.name,
        primaryAgentRef: cfg.agentRef,
        challengerAgentRef: cfg.challengerAgentId,
        maxRounds: cfg.maxRounds,
        resolutionPrompt: cfg.resolutionPrompt,
        transition: cfg.transition as 'AUTO',
      };
    }
    nodes.push({ id: cid, backendStepId: st.stepId, kind, position: { x: 200, y }, data } as CanvasNode);
    y += gap;
    if (i > 0) {
      edges.push({
        id: `e-${i - 1}-${i}`,
        source: `n-${i - 1}`,
        target: cid,
      });
    }
  }

  const blueprintKeys = workflow.blueprintBodies ? Object.keys(workflow.blueprintBodies) : [];
  const unsupported = blueprintKeys.length > 0;
  const unsupportedReasons = unsupported
    ? [
        `${blueprintKeys.length} blueprint definition(s) are kept for export. The loop body is read-only here until nested editing is supported.`,
      ]
    : undefined;

  return {
    workflowId: workflow.id,
    workflowName: workflow.name,
    description: workflow.description,
    startNodeId: nodes[0]?.id ?? null,
    nodes,
    edges,
    artifacts: workflow.artifacts,
    blueprints: {},
    unsupported,
    unsupportedReasons,
  };
}

/** Host API workflow detail shape for optional import adapters. */
export type WorkflowDetailDto = {
  id: string;
  name: string;
  description?: string;
  steps?: Array<{
    kind: string;
    step?: {
      stepId: string;
      name: string;
      stepPrompt?: string;
      inputKeys?: string[];
      outputKeys?: string[];
      behaviour: Record<string, unknown> & { type: string };
    };
    blueprintRef?: { blueprintId: string };
    nestedWorkflow?: unknown;
  }>;
  artifacts?: Record<string, { items?: EditableArtifactItem[] }>;
  blueprints?: Record<string, unknown>;
};

function detailStr(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function detailRecord(value: unknown): Record<string, string> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const out: Record<string, string> = {};
  for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
    if (typeof entry === 'string') {
      out[key] = entry;
    }
  }
  return out;
}

export function workflowDetailToCanvas(detail: WorkflowDetailDto): CanvasModel {
  const unsupportedReasons: string[] = [];
  for (const ex of detail.steps ?? []) {
    if (ex.kind !== 'STEP' || !ex.step) {
      if (ex.blueprintRef?.blueprintId) {
        unsupportedReasons.push(
          `Blueprint reference "${ex.blueprintRef.blueprintId}" in the main flow is read-only here until editing support is ready.`,
        );
      } else if (ex.kind === 'WORKFLOW' && ex.nestedWorkflow) {
        unsupportedReasons.push('A nested workflow in the main flow is read-only here until editing support is ready.');
      } else {
        unsupportedReasons.push(
          `Executable of kind "${ex.kind}" in the main flow is read-only here until editing support is ready.`,
        );
      }
    }
  }
  const blueprintCount = Object.keys(detail.blueprints ?? {}).length;
  if (blueprintCount > 0) {
    unsupportedReasons.push(
      `${blueprintCount} blueprint definition(s) on this workflow are preserved for export; the canvas shows top-level steps only.`,
    );
  }
  const unsupported = unsupportedReasons.length > 0;
  const wf = {
    id: detail.id,
    name: detail.name,
    description: detail.description ?? '',
    steps: [] as EditableStep[],
    artifacts: {} as Record<string, EditableArtifact>,
  };
  for (const ex of detail.steps ?? []) {
    if (ex.kind !== 'STEP' || !ex.step) {
      continue;
    }
    const s = ex.step;
    const b = s.behaviour;
    const type = b.type;
    const base: EditableStep = {
      stepId: s.stepId,
      name: s.name,
      behaviourType: 'INPUT',
      config: { artifactId: '', transition: 'AUTO' },
      stepPrompt: s.stepPrompt ?? '',
      contextMapping: {
        inputKeys: s.inputKeys ?? [],
        outputKeys: s.outputKeys ?? [],
      },
    };
    if (type === 'INPUT') {
      base.behaviourType = 'INPUT';
      base.config = {
        artifactId: detailStr(b.artifactId),
        transition: detailStr(b.transition, 'AUTO') as 'AUTO',
      };
    } else if (type === 'AGENT') {
      base.behaviourType = 'AGENT';
      base.config = {
        agentRef: detailStr(b.agentRef),
        transition: detailStr(b.transition, 'AUTO') as 'AUTO',
        maxRetries: (b.retryPolicy as { maxAttempts?: number } | null)?.maxAttempts ?? 0,
      };
    } else if (type === 'WORKFLOW') {
      base.behaviourType = 'WORKFLOW_BEHAVIOUR';
      base.config = {
        workflowRef: detailStr(b.workflowRef),
        transition: detailStr(b.transition, 'AUTO') as 'AUTO',
      };
    } else if (type === 'BRANCH') {
      base.behaviourType = 'BRANCH';
      base.config = {
        contextKey: detailStr(b.contextKey),
        branches: detailRecord(b.branchTargets),
        defaultBranch: detailStr(b.defaultBranchTargetId),
      };
    } else if (type === 'RESOURCE') {
      base.behaviourType = 'RESOURCE';
      base.config = {
        resourcePath: detailStr(b.resourcePath, '/schema/'),
        contextKey: detailStr(b.contextKey),
        transition: detailStr(b.transition, 'AUTO') as 'AUTO',
      };
    } else if (type === 'FAIL') {
      base.behaviourType = 'FAIL';
      base.config = { reason: detailStr((b as { reason?: unknown }).reason) };
    } else if (type === 'RETRY_PREVIOUS') {
      base.behaviourType = 'RETRY_PREVIOUS';
      base.config = {
        retryStepId: detailStr((b as { retryStepId?: unknown }).retryStepId),
        retryMode: 'SINGLE_STEP',
        maxAttempts: (b as { maxAttempts?: number }).maxAttempts ?? 1,
        fallback: detailStr((b as { fallback?: unknown }).fallback),
      };
    } else if (type === 'SPAR') {
      base.behaviourType = 'SPAR';
      const sc = (b.sparConfig as { challengerAgentId?: string; maxRounds?: number; resolutionPrompt?: string }) ?? {};
      base.config = {
        agentRef: detailStr(b.agentRef),
        challengerAgentId: sc.challengerAgentId ?? '',
        maxRounds: sc.maxRounds ?? 1,
        resolutionPrompt: sc.resolutionPrompt ?? '',
        transition: detailStr(b.transition, 'AUTO') as 'AUTO',
      };
    }
    wf.steps.push(base);
  }
  for (const [k, v] of Object.entries(detail.artifacts ?? {})) {
    const a = v as { items?: EditableArtifactItem[] };
    wf.artifacts[k] = { id: k, items: Array.isArray(a.items) ? a.items : [] };
  }
  const model = workflowToCanvas(wf as WorkflowDefinition);
  return {
    ...model,
    unsupported,
    unsupportedReasons: unsupported ? unsupportedReasons : undefined,
  };
}
