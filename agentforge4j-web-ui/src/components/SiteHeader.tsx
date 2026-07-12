// SPDX-License-Identifier: Apache-2.0
import { ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import { GITHUB_URL, NAV_CTA, PRIMARY_NAV } from '@/config/nav';

export default function SiteHeader() {
  return (
    <header className="border-b border-border bg-bg-elevated">
      <div className="mx-auto flex h-20 flex-wrap items-center gap-6 px-6" style={{ maxWidth: 'var(--max-content-width)' }}>
        <Link to="/" aria-label="AgentForge4j" className="flex items-center">
          <img src="/brand/logo-horizontal.svg" alt="AgentForge4j" className="h-16 w-auto block" />
        </Link>
        <nav aria-label="Primary" className="flex flex-1 flex-wrap items-center gap-6">
          {PRIMARY_NAV.map((item) => (
            <Link key={item.to} to={item.to} className="text-sm font-medium text-fg hover:text-brand">
              {item.label}
            </Link>
          ))}
        </nav>
        <a
          href={GITHUB_URL}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-1 text-sm font-medium text-fg-muted hover:text-fg"
        >
          GitHub
          <ExternalLink size={14} aria-hidden="true" />
        </a>
        <Link
          to={NAV_CTA.to}
          className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-ink hover:bg-brand-shade"
        >
          {NAV_CTA.label}
        </Link>
      </div>
    </header>
  );
}
