// SPDX-License-Identifier: Apache-2.0

import type { EditableArtifact, EditableArtifactItem } from '../api/types';
import type { NodeKind } from './nodeKinds';
import type { StepTransition } from '../api/types';

export type LoopTerminationStrategy = 'AGENT_SIGNAL' | 'EVALUATOR' | 'FIXED_COUNT' | 'FOR_EACH';

export type MaxIterationsActionUi = 'AWAIT_USER' | 'FAIL';

export type ArtifactItemDraft = {
  id: string;
  type: EditableArtifactItem['type'];
  label: string;
  key: string;
  required: boolean;
  options?: string[];
};

export type AskUserNodeData = {
  name: string;
  question: string;
  artifactItems: ArtifactItemDraft[];
};

export type AiStepNodeData = {
  name: string;
  agentRef: string;
  instructions: string;
  transition: StepTransition;
  maxRetries: number;
};

export type AiDebateNodeData = {
  name: string;
  primaryAgentRef: string;
  challengerAgentRef: string;
  maxRounds: number;
  resolutionPrompt: string;
  transition: StepTransition;
};

export type DecisionCaseDraft = {
  label: string;
  value: string;
  targetNodeId: string;
};

export type DecisionNodeData = {
  name: string;
  contextKey: string;
  cases: DecisionCaseDraft[];
  defaultTargetNodeId: string;
};

export type RepeatNodeData = {
  name: string;
  strategy: LoopTerminationStrategy;
  maxIterations: number;
  forEachKey?: string;
  evaluatorAgentId?: string;
  maxIterationsAction: MaxIterationsActionUi;
  bodyNodeIds: string[];
};

export type SaveResultNodeData = {
  name: string;
  resultName: string;
};

export type ReuseWorkflowNodeData = {
  name: string;
  workflowRef: string;
  transition: StepTransition;
};

export type LoadResourceNodeData = {
  name: string;
  resourcePath: string;
  resultName: string;
  transition: StepTransition;
};

export type StopNodeData = {
  name: string;
  reason: string;
};

export type RetryNodeData = {
  name: string;
  targetNodeId: string;
  maxAttempts: number;
  fallbackTargetNodeId: string;
  retryMode: 'SINGLE_STEP' | 'FROM_STEP';
};

export type NodeDataByKind = {
  ASK_USER: AskUserNodeData;
  AI_STEP: AiStepNodeData;
  AI_DEBATE: AiDebateNodeData;
  DECISION: DecisionNodeData;
  REPEAT: RepeatNodeData;
  SAVE_RESULT: SaveResultNodeData;
  REUSE_WORKFLOW: ReuseWorkflowNodeData;
  LOAD_RESOURCE: LoadResourceNodeData;
  STOP: StopNodeData;
  RETRY: RetryNodeData;
};

export type NodeData = NodeDataByKind[NodeKind];

type CanvasNodeBase = {
  id: string;
  backendStepId?: string;
  position: { x: number; y: number };
  parentNode?: string;
};

export type CanvasNode =
  | (CanvasNodeBase & { kind: 'ASK_USER'; data: AskUserNodeData })
  | (CanvasNodeBase & { kind: 'AI_STEP'; data: AiStepNodeData })
  | (CanvasNodeBase & { kind: 'AI_DEBATE'; data: AiDebateNodeData })
  | (CanvasNodeBase & { kind: 'DECISION'; data: DecisionNodeData })
  | (CanvasNodeBase & { kind: 'REPEAT'; data: RepeatNodeData })
  | (CanvasNodeBase & { kind: 'SAVE_RESULT'; data: SaveResultNodeData })
  | (CanvasNodeBase & { kind: 'REUSE_WORKFLOW'; data: ReuseWorkflowNodeData })
  | (CanvasNodeBase & { kind: 'LOAD_RESOURCE'; data: LoadResourceNodeData })
  | (CanvasNodeBase & { kind: 'STOP'; data: StopNodeData })
  | (CanvasNodeBase & { kind: 'RETRY'; data: RetryNodeData });

export type CanvasEdge = {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string | null;
  label?: string | null;
};

export type BlueprintDefinition = Record<string, unknown>;

export type CanvasModel = {
  workflowId: string;
  workflowName: string;
  description: string;
  startNodeId: string | null;
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  artifacts: Record<string, EditableArtifact>;
  blueprints: Record<string, BlueprintDefinition>;
  unsupported?: boolean;
  unsupportedReasons?: string[];
};
