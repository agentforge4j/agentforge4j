// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS, ISSUE_REWRITES } from '../copy/workflow-terminology';
import type { DraftValidationIssue } from '../hooks/useWorkflowDraft';
import type { CanvasModel } from '../model/canvasModel';
import { NODE_KIND_META } from '../model/nodeKinds';

type ValidationPanelProps = {
  model: CanvasModel;
  clientIssues: DraftValidationIssue[];
  serverIssues?: DraftValidationIssue[];
  onFix: (stepId?: string) => void;
};

type IssueGroup = {
  key: string;
  title: string;
  issues: DraftValidationIssue[];
};

function rewriteIssue(message: string): string {
  const normalized = message.trim().toLowerCase();
  const matched = Object.entries(ISSUE_REWRITES).find(([key]) => normalized.includes(key));
  return matched?.[1] ?? message;
}

function severityGlyph(issue: DraftValidationIssue): string {
  if (issue.code.toLowerCase().includes('warning')) {
    return '⚠';
  }
  return '!';
}

function groupIssues(model: CanvasModel, issues: DraftValidationIssue[]): IssueGroup[] {
  const grouped = new Map<string, IssueGroup>();
  for (const issue of issues) {
    const stepId = issue.stepId?.trim();
    if (!stepId) {
      const fallback = grouped.get('workflow') ?? { key: 'workflow', title: 'Workflow', issues: [] };
      fallback.issues.push(issue);
      grouped.set('workflow', fallback);
      continue;
    }
    const node = model.nodes.find((entry) => entry.backendStepId === stepId);
    const defaultTitle = node ? `${NODE_KIND_META[node.kind].label} · ${node.data.name?.trim() || 'Untitled step'}` : stepId;
    const entry = grouped.get(stepId) ?? { key: stepId, title: defaultTitle, issues: [] };
    entry.issues.push(issue);
    grouped.set(stepId, entry);
  }
  return [...grouped.values()];
}

function Section({
  title,
  groups,
  onFix,
}: {
  title: string;
  groups: IssueGroup[];
  onFix: (stepId?: string) => void;
}) {
  if (groups.length === 0) {
    return null;
  }
  return (
    <div className="wf-validation-panel__section">
      <h4 className="wf-validation-panel__section-title">{title}</h4>
      {groups.map((group) => (
        <details key={group.key} className="wf-validation-panel__group" open>
          <summary className="wf-validation-panel__group-summary">{group.title}</summary>
          <ul className="wf-validation-panel__issue-list">
            {group.issues.map((issue, index) => (
              <li key={`${group.key}-${issue.code}-${index}`} className="wf-validation-panel__issue">
                <div className="wf-validation-panel__issue-content">
                  <span className="wf-validation-panel__issue-glyph" aria-hidden>
                    {severityGlyph(issue)}
                  </span>
                  <span className="wf-validation-panel__issue-message">{rewriteIssue(issue.message)}</span>
                </div>
                <button
                  type="button"
                  className="wf-button wf-button--secondary wf-validation-panel__fix-button"
                  disabled={!issue.stepId}
                  onClick={() => {
                    if (!issue.stepId) {
                      console.info('Validation issue has no stepId; cannot focus specific step.', issue);
                    }
                    onFix(issue.stepId);
                  }}
                >
                  {ACTION_LABELS.fixIssue}
                </button>
              </li>
            ))}
          </ul>
        </details>
      ))}
    </div>
  );
}

export function ValidationPanel({ model, clientIssues, serverIssues = [], onFix }: ValidationPanelProps) {
  const clientGroups = groupIssues(model, clientIssues);
  const serverGroups = groupIssues(model, serverIssues);
  const totalIssues = clientIssues.length + serverIssues.length;

  return (
    <details className="wf-validation-panel" open={totalIssues > 0}>
      <summary className="wf-validation-panel__summary">
        <span className="wf-validation-panel__summary-mobile">
          {totalIssues === 0 ? ACTION_LABELS.workflowLooksGood : ACTION_LABELS.thingsToFix(totalIssues)}
        </span>
        <span className="wf-validation-panel__summary-desktop">{ACTION_LABELS.clientValidation}</span>
        <span className="wf-validation-panel__chevron" aria-hidden>
          ▾
        </span>
      </summary>
      <div className="wf-validation-panel__body">
        {totalIssues === 0 ? (
          <p className="wf-validation-panel__ok">{ACTION_LABELS.checkmarkOk}</p>
        ) : (
          <>
            <p className="wf-validation-panel__count">{ACTION_LABELS.thingsToFix(totalIssues)}</p>
            <Section title={ACTION_LABELS.clientValidation} groups={clientGroups} onFix={onFix} />
            <Section title={ACTION_LABELS.serverValidation} groups={serverGroups} onFix={onFix} />
          </>
        )}
      </div>
    </details>
  );
}
