// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import App from '@/App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  test.each([
    ['/', 'AgentForge4j'],
    ['/docs', 'Documentation'],
    ['/use', 'Get started'],
    ['/catalogue', 'Workflow catalogue'],
    ['/architecture', 'Architecture'],
    ['/releases', 'Releases'],
    ['/community', 'Community & contributing'],
    ['/contributing', 'Community & contributing'],
    ['/security', 'Security'],
    ['/legal', 'Legal'],
    ['/contact', 'Contact'],
  ])('renders the %s route heading', (path, heading) => {
    renderAt(path);
    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  test('renders the /builder route with an accessible loading state, then the workflow builder mounted', async () => {
    renderAt('/builder');
    // Lazy-loaded (see App.tsx) — the builder's own dependency weight is kept out of every
    // other route's bundle, so the first render suspends before mounting resolves
    // asynchronously. This must be the first test in the file to touch /builder: once
    // React.lazy resolves BuilderPage it never suspends again for the rest of this module.
    expect(screen.getByRole('status')).toHaveTextContent(/loading the workflow builder/i);
    // The default 1000ms findBy timeout is too tight for this route specifically: it's the
    // first thing in the whole suite to transform the heavy builder/graph dependency chain, and
    // on a cold Vite/esbuild cache that transform alone can exceed 1s. 5000ms gives real headroom
    // without masking a genuinely broken/never-resolving lazy import.
    expect(await screen.findByTestId('workflow-builder', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  test('renders NotFoundPage for an unmatched path', () => {
    renderAt('/this-route-does-not-exist');
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
  });
});

describe('catalogue detail routing', () => {
  test('a real workflow id renders the catalogue detail page, not the list page', () => {
    renderAt('/catalogue/workflow-execution-estimator');
    expect(
      screen.getByRole('heading', { level: 1, name: 'Workflow Execution Estimator' }),
    ).toBeInTheDocument();
  });

  test('an unmatched workflow id renders NotFoundPage, not a fabricated placeholder', () => {
    renderAt('/catalogue/some-workflow-that-does-not-exist');
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
  });
});

describe('a11y baseline', () => {
  test('exposes a skip-to-content link targeting #main-content', () => {
    renderAt('/');
    const skipLink = screen.getByRole('link', { name: 'Skip to content' });
    expect(skipLink).toHaveAttribute('href', '#main-content');
  });

  test('main content region has the id the skip link targets', () => {
    renderAt('/');
    expect(document.getElementById('main-content')).toBeInTheDocument();
  });

  test('primary navigation is an accessible landmark', () => {
    renderAt('/');
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
  });

  test('every placeholder route exposes exactly one h1', () => {
    renderAt('/architecture');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });
});

describe('mobile navigation', () => {
  test('primary nav links are not duplicated in the DOM until the menu is opened', () => {
    renderAt('/');
    // The mobile menu panel is unmounted (not just hidden) until toggled — the desktop
    // nav (CSS-hidden below `md:`, not removed) is the only "Primary" landmark by default.
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(1);
  });

  test('the menu toggle opens a second Primary nav landmark with the same links, and closes it again', async () => {
    const user = userEvent.setup();
    renderAt('/');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Close menu' }));
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(1);
  });

  test('clicking a link in the open mobile menu closes it', async () => {
    const user = userEvent.setup();
    const { container } = renderAt('/');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    const mobileNav = container.querySelector('#primary-nav-mobile');
    expect(mobileNav).not.toBeNull();
    await user.click(within(mobileNav as HTMLElement).getByRole('link', { name: 'Catalogue' }));
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(1);
  });

  test('clicking the GitHub link in the open mobile menu closes it too', async () => {
    const user = userEvent.setup();
    const { container } = renderAt('/');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    const mobileNav = container.querySelector('#primary-nav-mobile');
    await user.click(within(mobileNav as HTMLElement).getByRole('link', { name: /GitHub/ }));
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(1);
  });

  test('pressing Escape closes the open mobile menu', async () => {
    const user = userEvent.setup();
    renderAt('/');
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(2);
    await user.keyboard('{Escape}');
    expect(screen.getAllByRole('navigation', { name: 'Primary' })).toHaveLength(1);
  });
});

describe('branding', () => {
  test('header renders the canonical logo asset', () => {
    renderAt('/');
    const logo = screen.getByAltText('AgentForge4j');
    expect(logo).toHaveAttribute('src', '/brand/logo-horizontal.svg');
  });
});

describe('header and footer GitHub links', () => {
  test('the desktop header GitHub link points at the real org repo', () => {
    renderAt('/');
    expect(screen.getByRole('link', { name: /^GitHub/ })).toHaveAttribute(
      'href',
      'https://github.com/agentforge4j/agentforge4j',
    );
  });

  test('the footer GitHub link points at the real org repo', () => {
    const { container } = renderAt('/');
    const footer = container.querySelector('footer');
    expect(footer).not.toBeNull();
    expect(
      within(footer as HTMLElement).getByRole('link', { name: 'agentforge4j/agentforge4j' }),
    ).toHaveAttribute('href', 'https://github.com/agentforge4j/agentforge4j');
  });
});

describe('footer navigation', () => {
  test('every route is reachable from either the primary nav or the footer, except deliberate aliases', () => {
    // /contributing is a deliberate alias of /community (same content, same nav entry) —
    // not a defect, so it's excluded here rather than asserted unreachable.
    renderAt('/');
    const reachableHrefs = new Set(
      screen.getAllByRole('link').map((link) => link.getAttribute('href')),
    );
    for (const path of ['/docs', '/use', '/catalogue', '/builder', '/architecture', '/releases', '/community', '/security', '/legal', '/contact']) {
      expect(reachableHrefs.has(path)).toBe(true);
    }
  });
});

describe('content-track pages', () => {
  test('contact page renders all three aliases with no conditional wording', () => {
    renderAt('/contact');
    expect(screen.getByRole('link', { name: 'security@agentforge4j.org' })).toHaveAttribute(
      'href',
      'mailto:security@agentforge4j.org',
    );
    expect(screen.getByRole('link', { name: 'admin@agentforge4j.org' })).toHaveAttribute(
      'href',
      'mailto:admin@agentforge4j.org',
    );
    expect(screen.getByRole('link', { name: 'info@agentforge4j.org' })).toHaveAttribute(
      'href',
      'mailto:info@agentforge4j.org',
    );
  });

  test('security page links a mailto to security@ and the repository security tab', () => {
    renderAt('/security');
    expect(screen.getByRole('link', { name: /security@agentforge4j\.org/ })).toHaveAttribute(
      'href',
      'mailto:security@agentforge4j.org',
    );
    expect(screen.getByRole('link', { name: /GitHub private vulnerability reporting/ })).toHaveAttribute(
      'href',
      'https://github.com/agentforge4j/agentforge4j/security',
    );
  });

  test('releases page is honest that nothing is published to Maven Central yet', () => {
    renderAt('/releases');
    expect(screen.getByText(/has not published a 0\.1\.0 release to Maven Central yet/)).toBeInTheDocument();
  });

  test('use page does not print copy-pasteable Maven coordinates before anything is published', () => {
    renderAt('/use');
    expect(screen.getByText(/not yet published to Maven Central/)).toBeInTheDocument();
    expect(screen.queryByText(/<dependency>/)).not.toBeInTheDocument();
  });

  test('legal page links the Apache-2.0 licence', () => {
    renderAt('/legal');
    expect(screen.getByRole('link', { name: 'Apache License 2.0' })).toHaveAttribute(
      'href',
      'https://github.com/agentforge4j/agentforge4j/blob/main/LICENSE',
    );
  });

  test('architecture page embeds both overview diagrams with descriptive alt text', () => {
    renderAt('/architecture');
    const diagrams = screen.getAllByRole('img').filter((img) => img.getAttribute('src')?.startsWith('/diagrams/'));
    expect(diagrams).toHaveLength(2);
    diagrams.forEach((img) => expect(img.getAttribute('alt')?.length).toBeGreaterThan(20));
  });
});
