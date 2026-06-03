// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from 'react';

type EmptyStateProps = {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  primaryAction?: {
    label: string;
    onClick: () => void;
  };
  secondaryAction?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
};

export function EmptyState({
  icon,
  title,
  description,
  action,
  primaryAction,
  secondaryAction,
  className,
}: EmptyStateProps) {
  const resolvedAction =
    action ??
    (primaryAction || secondaryAction ? (
      <div className="wf-empty-state__actions">
        {primaryAction ? (
          <button type="button" className="wf-button wf-button--primary" onClick={primaryAction.onClick}>
            {primaryAction.label}
          </button>
        ) : null}
        {secondaryAction ? (
          <button type="button" className="wf-button wf-button--secondary" onClick={secondaryAction.onClick}>
            {secondaryAction.label}
          </button>
        ) : null}
      </div>
    ) : null);

  return (
    <div className={['wf-empty-state', className].filter(Boolean).join(' ')}>
      {icon ?? null}
      <p className="wf-empty-state__title">{title}</p>
      {description ? <p className="wf-empty-state__description">{description}</p> : null}
      {resolvedAction}
    </div>
  );
}
