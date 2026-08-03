// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from 'react';
import { ExternalLink, Menu, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { GITHUB_URL, NAV_CTA, PRIMARY_NAV } from '@/config/nav';
import ThemeToggle from '@/components/ThemeToggle';
import { useTheme } from '@/theme/ThemeContext';

export default function SiteHeader() {
  const [menuOpen, setMenuOpen] = useState(false);
  const toggleButtonRef = useRef<HTMLButtonElement>(null);
  const { effectiveTheme } = useTheme();
  const logoSrc = effectiveTheme === 'dark' ? '/brand/logo-horizontal-dark.svg' : '/brand/logo-horizontal.svg';

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
        // Focus may be anywhere inside the now-closing panel (e.g. a nav link) — return it to
        // the toggle button rather than letting it fall back to document.body.
        toggleButtonRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  return (
    <header className="border-b border-border bg-bg-elevated">
      <div
        className="mx-auto flex h-20 items-center gap-6 px-6"
        style={{ maxWidth: 'var(--max-content-width)' }}
      >
        <Link
          to="/"
          aria-label="AgentForge4j"
          className="flex shrink-0 items-center"
          onClick={() => setMenuOpen(false)}
        >
          <img src={logoSrc} alt="AgentForge4j" className="h-16 w-auto block" />
        </Link>

        <nav aria-label="Primary" className="hidden flex-1 items-center gap-6 lg:flex">
          {PRIMARY_NAV.map((item) =>
            item.external ? (
              <a key={item.to} href={item.to} className="text-sm font-medium text-fg hover:text-brand">
                {item.label}
              </a>
            ) : (
              <Link key={item.to} to={item.to} className="text-sm font-medium text-fg hover:text-brand">
                {item.label}
              </Link>
            ),
          )}
        </nav>
        <a
          href={GITHUB_URL}
          target="_blank"
          rel="noreferrer"
          className="hidden items-center gap-1 text-sm font-medium text-fg-muted hover:text-fg lg:flex"
        >
          GitHub
          <ExternalLink size={14} aria-hidden="true" />
        </a>
        <Link
          to={NAV_CTA.to}
          className="hidden rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-ink hover:bg-brand-shade lg:inline-block"
        >
          {NAV_CTA.label}
        </Link>

        <div className="ml-auto lg:ml-0">
          <ThemeToggle />
        </div>

        <button
          ref={toggleButtonRef}
          type="button"
          aria-expanded={menuOpen}
          aria-controls="primary-nav-mobile"
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          onClick={() => setMenuOpen((open) => !open)}
          className="flex items-center justify-center rounded-md p-2 text-fg lg:hidden"
        >
          {menuOpen ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
        </button>
      </div>

      {menuOpen && (
        <nav
          id="primary-nav-mobile"
          aria-label="Primary"
          className="flex flex-col gap-1 border-t border-border px-6 py-4 lg:hidden"
        >
          {PRIMARY_NAV.map((item) =>
            item.external ? (
              <a
                key={item.to}
                href={item.to}
                onClick={() => setMenuOpen(false)}
                className="rounded-md px-2 py-2 text-sm font-medium text-fg hover:bg-bg hover:text-brand"
              >
                {item.label}
              </a>
            ) : (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setMenuOpen(false)}
                className="rounded-md px-2 py-2 text-sm font-medium text-fg hover:bg-bg hover:text-brand"
              >
                {item.label}
              </Link>
            ),
          )}
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            onClick={() => setMenuOpen(false)}
            className="flex items-center gap-1 rounded-md px-2 py-2 text-sm font-medium text-fg-muted hover:bg-bg hover:text-fg"
          >
            GitHub
            <ExternalLink size={14} aria-hidden="true" />
          </a>
          <Link
            to={NAV_CTA.to}
            onClick={() => setMenuOpen(false)}
            className="mt-2 rounded-md bg-brand px-4 py-2 text-center text-sm font-semibold text-brand-ink hover:bg-brand-shade"
          >
            {NAV_CTA.label}
          </Link>
        </nav>
      )}
    </header>
  );
}
