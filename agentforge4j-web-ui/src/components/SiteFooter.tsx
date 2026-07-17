// SPDX-License-Identifier: Apache-2.0
import { Link } from 'react-router-dom';
import { FOOTER_COLUMNS, GITHUB_URL } from '@/config/nav';

export default function SiteFooter() {
  return (
    <footer className="border-t border-border bg-bg-elevated mt-auto">
      <div className="mx-auto px-6 py-10" style={{ maxWidth: 'var(--max-content-width)' }}>
        <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
          {FOOTER_COLUMNS.map((column) => (
            <div key={column.heading}>
              <h2 className="text-sm font-semibold text-fg">{column.heading}</h2>
              <ul className="mt-3 space-y-2">
                {column.links.map((link) => (
                  <li key={link.to}>
                    {link.external ? (
                      <a href={link.to} className="text-sm text-fg-muted hover:text-fg">
                        {link.label}
                      </a>
                    ) : (
                      <Link to={link.to} className="text-sm text-fg-muted hover:text-fg">
                        {link.label}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
          <div>
            <h2 className="text-sm font-semibold text-fg">GitHub</h2>
            <ul className="mt-3 space-y-2">
              <li>
                <a
                  href={GITHUB_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="break-words text-sm text-fg-muted hover:text-fg"
                >
                  agentforge4j/agentforge4j
                </a>
              </li>
            </ul>
          </div>
        </div>
        <div className="mt-8 border-t border-border pt-6 text-sm text-fg-muted">
          AgentForge4j — Apache 2.0
        </div>
      </div>
    </footer>
  );
}
