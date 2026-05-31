// SPDX-License-Identifier: Apache-2.0

import {
  Bot,
  CircleStop,
  FileInput,
  MessageCircleQuestion,
  Repeat,
  RotateCcw,
  Save,
  Split,
  Wrench,
  Workflow,
  type LucideIcon,
} from 'lucide-react';
import { NODE_DESCRIPTIONS, NODE_LABELS } from '../copy/workflow-terminology';

export type NodeKind =
  | 'ASK_USER'
  | 'AI_STEP'
  | 'AI_DEBATE'
  | 'DECISION'
  | 'REPEAT'
  | 'REUSE_WORKFLOW'
  | 'LOAD_RESOURCE'
  | 'STOP'
  | 'RETRY'
  | 'SAVE_RESULT';

export type NodeKindMeta = {
  label: string;
  description: string;
  iconGlyph: string;
  accentVar: string;
  Icon: LucideIcon;
  advanced?: boolean;
};

export const NODE_KIND_META: Record<NodeKind, NodeKindMeta> = {
  ASK_USER: {
    label: NODE_LABELS.ASK_USER,
    description: NODE_DESCRIPTIONS.ASK_USER,
    iconGlyph: '?',
    accentVar: '--afb-kind-input',
    Icon: MessageCircleQuestion,
  },
  AI_STEP: {
    label: NODE_LABELS.AI_STEP,
    description: NODE_DESCRIPTIONS.AI_STEP,
    iconGlyph: 'AI',
    accentVar: '--afb-kind-ai',
    Icon: Bot,
  },
  AI_DEBATE: {
    label: NODE_LABELS.AI_DEBATE,
    description: NODE_DESCRIPTIONS.AI_DEBATE,
    iconGlyph: 'DB',
    accentVar: '--afb-kind-tools',
    Icon: Wrench,
    advanced: true,
  },
  DECISION: {
    label: NODE_LABELS.DECISION,
    description: NODE_DESCRIPTIONS.DECISION,
    iconGlyph: 'IF',
    accentVar: '--afb-kind-decision',
    Icon: Split,
  },
  REPEAT: {
    label: NODE_LABELS.REPEAT,
    description: NODE_DESCRIPTIONS.REPEAT,
    iconGlyph: '↻',
    accentVar: '--afb-kind-loop',
    Icon: Repeat,
  },
  REUSE_WORKFLOW: {
    label: NODE_LABELS.REUSE_WORKFLOW,
    description: NODE_DESCRIPTIONS.REUSE_WORKFLOW,
    iconGlyph: 'WF',
    accentVar: '--afb-kind-reuse',
    Icon: Workflow,
  },
  LOAD_RESOURCE: {
    label: NODE_LABELS.LOAD_RESOURCE,
    description: NODE_DESCRIPTIONS.LOAD_RESOURCE,
    iconGlyph: 'LD',
    accentVar: '--afb-kind-resource',
    Icon: FileInput,
    advanced: true,
  },
  STOP: {
    label: NODE_LABELS.STOP,
    description: NODE_DESCRIPTIONS.STOP,
    iconGlyph: '!',
    accentVar: '--afb-kind-stop',
    Icon: CircleStop,
    advanced: true,
  },
  RETRY: {
    label: NODE_LABELS.RETRY,
    description: NODE_DESCRIPTIONS.RETRY,
    iconGlyph: 'RT',
    accentVar: '--afb-kind-retry',
    Icon: RotateCcw,
    advanced: true,
  },
  SAVE_RESULT: {
    label: NODE_LABELS.SAVE_RESULT,
    description: NODE_DESCRIPTIONS.SAVE_RESULT,
    iconGlyph: 'SV',
    accentVar: '--afb-kind-save',
    Icon: Save,
  },
};

export const LIBRARY_COMMON_KINDS: NodeKind[] = ['ASK_USER', 'AI_STEP', 'SAVE_RESULT'];

export const LIBRARY_FLOW_KINDS: NodeKind[] = ['DECISION', 'REPEAT', 'REUSE_WORKFLOW'];

export const LIBRARY_ADVANCED_KINDS: NodeKind[] = ['AI_DEBATE', 'LOAD_RESOURCE', 'STOP', 'RETRY'];
