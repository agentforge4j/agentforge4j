// SPDX-License-Identifier: Apache-2.0
import { Link } from 'react-router-dom';
import { USE_COPY } from '@/copy/use';

export default function UsePage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Get started</h1>
      <p className="mt-4 max-w-2xl text-fg">{USE_COPY.intro}</p>

      <h2 className="mt-10 text-lg font-semibold text-fg">Coordinates</h2>
      <p className="mt-2 max-w-2xl text-sm text-fg-muted">
        Group ID: <code className="rounded bg-bg-elevated px-1.5 py-0.5">{USE_COPY.groupId}</code>
      </p>
      <p className="mt-2 max-w-2xl text-sm text-fg-muted">
        Version: <code className="rounded bg-bg-elevated px-1.5 py-0.5">{USE_COPY.version}</code>,
        published to Maven Central — for example{' '}
        <code className="rounded bg-bg-elevated px-1.5 py-0.5">{USE_COPY.primaryArtifactId}</code>.
      </p>
      <p className="mt-2 max-w-2xl text-sm text-fg-muted">
        See the{' '}
        <Link to="/releases" className="text-brand underline">
          Releases
        </Link>{' '}
        page for what's shipped so far.
      </p>

      <h2 className="mt-10 text-lg font-semibold text-fg">What you need</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-fg">
        {USE_COPY.requirements.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>

      <h2 className="mt-10 text-lg font-semibold text-fg">Embedding the framework</h2>
      <ol className="mt-3 space-y-4">
        {USE_COPY.embedSteps.map((step, index) => (
          <li key={step.heading} className="flex gap-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand text-xs font-semibold text-brand-ink">
              {index + 1}
            </span>
            <span>
              <span className="font-medium text-fg">{step.heading}.</span>{' '}
              <span className="text-sm text-fg-muted">{step.body}</span>
            </span>
          </li>
        ))}
      </ol>

      <p className="mt-10 text-sm text-fg-muted">
        For the full API reference and guides, see the{' '}
        <a href="/docs/" className="text-brand underline">
          documentation
        </a>
        .
      </p>
    </div>
  );
}
