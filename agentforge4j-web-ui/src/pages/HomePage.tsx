// SPDX-License-Identifier: Apache-2.0
import { Link } from 'react-router-dom';
import { HOME_COPY } from '@/copy/home';

export default function HomePage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-4xl font-bold text-fg">AgentForge4j</h1>
      <p className="mt-3 text-xl text-fg-muted">{HOME_COPY.tagline}</p>
      <p className="mt-6 max-w-3xl text-base text-fg">{HOME_COPY.intro}</p>

      <div className="mt-8 flex flex-wrap gap-4">
        <Link to="/use" className="rounded-md bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-shade">
          Get started
        </Link>
        <Link to="/catalogue" className="rounded-md border border-border px-5 py-2.5 text-sm font-semibold text-fg hover:bg-bg-elevated">
          Browse the catalogue
        </Link>
      </div>

      <div className="mt-16 grid gap-8 sm:grid-cols-3">
        {HOME_COPY.pillars.map((pillar) => (
          <div key={pillar.heading}>
            <h2 className="text-lg font-semibold text-fg">{pillar.heading}</h2>
            <p className="mt-2 text-sm text-fg-muted">{pillar.body}</p>
          </div>
        ))}
      </div>

      <p className="mt-16 text-sm text-fg-muted">{HOME_COPY.status}</p>
    </div>
  );
}
