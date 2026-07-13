// SPDX-License-Identifier: Apache-2.0
import { Link } from 'react-router-dom';
import { catalogueData } from '@/lib/catalogueData';
import { CATALOGUE_COPY } from '@/copy/catalogue';

export default function CataloguePage() {
  const { workflows } = catalogueData;

  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">{CATALOGUE_COPY.listHeading}</h1>
      <p className="mt-4 max-w-2xl text-fg">{CATALOGUE_COPY.listIntro}</p>

      {workflows.length === 0 ? (
        <p className="mt-8 text-fg-muted">{CATALOGUE_COPY.emptyState}</p>
      ) : (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2">
          {workflows.map((workflow) => (
            <li key={workflow.id} className="rounded-lg border border-border p-6">
              <h2 className="text-xl font-semibold text-fg">
                <Link to={`/catalogue/${workflow.id}`} className="hover:underline">
                  {workflow.name}
                </Link>
              </h2>
              {workflow.description && (
                <p className="mt-2 text-sm text-fg-muted">{workflow.description}</p>
              )}
              <span className="mt-4 inline-block rounded-full bg-success/10 px-3 py-1 text-sm font-medium text-success-ink">
                {CATALOGUE_COPY.shippedBadge}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
