// SPDX-License-Identifier: Apache-2.0
import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { catalogueData } from '@/lib/catalogueData';
import { renderWorkflowSvg } from '@/lib/renderWorkflowSvg';
import { CATALOGUE_COPY } from '@/copy/catalogue';
import NotFoundPage from '@/pages/NotFoundPage';

export default function CatalogueDetailPage() {
  const { id } = useParams<{ id: string }>();
  const workflow = catalogueData.workflows.find((entry) => entry.id === id);

  // Hooks must run unconditionally; compute against `workflow` (possibly undefined) and only
  // branch to the not-found render after.
  const svgMarkup = useMemo(() => (workflow ? renderWorkflowSvg(workflow.steps) : ''), [workflow]);

  if (!workflow) {
    return <NotFoundPage />;
  }

  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <Link to="/catalogue" className="text-sm text-brand underline">
        {CATALOGUE_COPY.backToList}
      </Link>

      <h1 className="mt-4 text-3xl font-semibold text-fg">{workflow.name}</h1>
      <span className="mt-3 inline-block rounded-full bg-success/10 px-3 py-1 text-sm font-medium text-success-ink">
        {CATALOGUE_COPY.shippedBadge}
      </span>

      {workflow.description && <p className="mt-4 max-w-2xl text-fg">{workflow.description}</p>}

      {(workflow.author || workflow.contact || workflow.version) && (
        <dl className="mt-8 grid gap-4 sm:grid-cols-3">
          {workflow.author && (
            <div>
              <dt className="text-sm font-medium text-fg-muted">{CATALOGUE_COPY.authorLabel}</dt>
              <dd className="text-fg">{workflow.author}</dd>
            </div>
          )}
          {workflow.contact && (
            <div>
              <dt className="text-sm font-medium text-fg-muted">{CATALOGUE_COPY.contactLabel}</dt>
              <dd className="text-fg">{workflow.contact}</dd>
            </div>
          )}
          {workflow.version && (
            <div>
              <dt className="text-sm font-medium text-fg-muted">{CATALOGUE_COPY.versionLabel}</dt>
              <dd className="text-fg">{workflow.version}</dd>
            </div>
          )}
        </dl>
      )}

      <section className="mt-10">
        <h2 className="text-lg font-semibold text-fg">{CATALOGUE_COPY.graphHeading}</h2>
        <div
          className="mt-4 max-w-full overflow-x-auto rounded-lg border border-border bg-bg-elevated p-4"
          role="img"
          aria-label={CATALOGUE_COPY.graphAltText(workflow.name)}
          // svgMarkup is generated at build time by this module's own renderWorkflowSvg from a
          // schema-validated build-time source (never user input), so injecting it is safe.
          dangerouslySetInnerHTML={{ __html: svgMarkup }}
        />
      </section>

      <Link
        to="/builder"
        className="mt-10 inline-block rounded-md bg-brand px-4 py-2 text-sm font-medium text-brand-ink hover:bg-brand-shade"
      >
        {CATALOGUE_COPY.openInBuilder}
      </Link>
      <p className="mt-2 max-w-2xl text-sm text-fg-muted">{CATALOGUE_COPY.openInBuilderNote}</p>
    </div>
  );
}
