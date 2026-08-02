// SPDX-License-Identifier: Apache-2.0
import type { ReactNode } from 'react';

interface PagePlaceholderProps {
  readonly title: string;
  readonly children?: ReactNode;
}

/**
 * Foundation-track content stand-in. Real copy for each route is authored by the
 * content/builder/catalogue tracks (design §13) — this only establishes the route,
 * heading, and layout shell so navigation has no dead links.
 */
export default function PagePlaceholder({ title, children }: PagePlaceholderProps) {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">{title}</h1>
      {children ?? <p className="mt-4 text-fg-muted">This page is under construction.</p>}
    </div>
  );
}
