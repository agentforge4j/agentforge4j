// SPDX-License-Identifier: Apache-2.0

import { Check, TriangleAlert, UserRoundCheck } from 'lucide-react';
import { NODE_STATUS_LABELS } from '../../copy/workflow-terminology';
import type { NodeKind } from '../../model/nodeKinds';
import { NODE_KIND_META } from '../../model/nodeKinds';
import type { CSSProperties, ReactNode } from 'react';

export type NodeVisualVariant = 'step' | 'decision' | 'loop';

export type NodeChromeProps = {
  kind: NodeKind;
  variant?: NodeVisualVariant;
  title: string;
  subtitle: string;
  selected?: boolean;
  issueCount?: number;
  needsApproval?: boolean;
  children?: ReactNode;
  className?: string;
};

function StatusBadge({ issueCount, needsApproval }: { issueCount: number; needsApproval: boolean }) {
  if (issueCount > 0) {
    return (
      <span className="wf-node-badge wf-node-badge--attention">
        <TriangleAlert className="wf-node-badge__icon" aria-hidden size={12} strokeWidth={2.25} />
        {NODE_STATUS_LABELS.hasIssues}
      </span>
    );
  }
  if (needsApproval) {
    return (
      <span className="wf-node-badge wf-node-badge--approval">
        <UserRoundCheck className="wf-node-badge__icon" aria-hidden size={12} strokeWidth={2.25} />
        {NODE_STATUS_LABELS.needsApproval}
      </span>
    );
  }
  return (
    <span className="wf-node-badge wf-node-badge--ready">
      <Check className="wf-node-badge__icon" aria-hidden size={12} strokeWidth={2.25} />
      {NODE_STATUS_LABELS.valid}
    </span>
  );
}

export function NodeChrome({
  kind,
  variant = 'step',
  title,
  subtitle,
  selected,
  issueCount = 0,
  needsApproval = false,
  children,
  className,
}: NodeChromeProps) {
  const meta = NODE_KIND_META[kind];
  const Icon = meta.Icon;
  const accentStyle = {
    ['--afb-node-accent' as string]: `var(${meta.accentVar})`,
  } as CSSProperties;

  return (
    <div
      className={[
        'wf-node',
        `wf-node--${variant}`,
        selected ? 'wf-node--selected' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={accentStyle}
    >
      <div className="wf-node__header">
        <span className="wf-node__icon" aria-hidden>
          <Icon size={14} strokeWidth={2.25} />
        </span>
        <div className="wf-node__titles">
          <p className="wf-node__kind">{meta.label}</p>
          <p className="wf-node__title">{title}</p>
        </div>
      </div>
      <div className="wf-node__body">
        <p className="wf-node__subtitle">{subtitle}</p>
      </div>
      <div className="wf-node__footer">
        <StatusBadge issueCount={issueCount} needsApproval={needsApproval && issueCount === 0} />
      </div>
      {children}
    </div>
  );
}
