// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test, vi } from 'vitest';
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
    ['/api', 'API reference'],
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
    // Lazy-loaded (see App.tsx) — the builder's own dependency weight is kept out of every
    // other route's bundle, so the first render suspends before mounting resolves
    // asynchronously. React.lazy caches its resolution on the App module, so this test
    // imports a fresh App instance (fresh lazy, guaranteed first-render suspension) instead
    // of depending on being the first test in the file to touch /builder — any other test
    // rendering /builder earlier (e.g. under shuffled test order) must not break this one.
    vi.resetModules();
    const { default: FreshApp } = await import('@/App');
    render(
      <MemoryRouter initialEntries={['/builder']}>
        <FreshApp />
      </MemoryRouter>,
    );
    expect(screen.getByRole('status')).toHaveTextContent(/loading the workflow builder/i);
    // The default 1000ms findBy timeout is too tight for this route specifically: it can be
    // the first thing in the suite to transform the heavy builder/graph dependency chain, and
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

describe('api nav placement', () => {
  test('/api appears in the primary navigation landmark, not footer-only', () => {
    renderAt('/');
    const primaryNav = screen.getByRole('navigation', { name: 'Primary' });
    expect(within(primaryNav).getByRole('link', { name: 'API' })).toHaveAttribute('href', '/api');
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
    for (const path of ['/docs', '/api', '/use', '/catalogue', '/builder', '/architecture', '/releases', '/community', '/security', '/legal', '/contact']) {
      expect(reachableHrefs.has(path)).toBe(true);
    }
  });
});

describe('builder full-viewport layout', () => {
  // Regression guard for the /builder page collapsing to the embedded builder's own
  // min-height with the marketing footer visible directly beneath it: the app shell must
  // pin this route to a single definite viewport height (the builder sizes itself with
  // height: 100%, which only resolves against a definite ancestor chain) and omit the
  // footer while editing.
  test('the app shell pins /builder to a definite viewport height and omits the footer', async () => {
    const { container } = renderAt('/builder');
    await screen.findByTestId('workflow-builder', {}, { timeout: 5000 });
    const shell = container.firstElementChild as HTMLElement;
    expect(shell).toHaveClass('h-dvh');
    expect(shell).not.toHaveClass('min-h-dvh');
    const main = document.getElementById('main-content') as HTMLElement;
    expect(main).toHaveClass('min-h-0');
    // The SITE footer specifically (role contentinfo — a top-level <footer>): the embedded
    // builder legitimately renders its own <footer> elements inside <main> (scoped out of
    // the contentinfo role), so a bare querySelector('footer') would be the wrong anchor.
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });

  test.each(['/builder/', '/Builder'])(
    'the %s URL variant the router matches to the builder route gets the builder shell too',
    async (path) => {
      // The route matcher is trailing-slash tolerant and case-insensitive; the shell must
      // key off the same matcher (useMatch), not an exact pathname compare, or these URLs
      // would render the builder inside the grow-to-content marketing shell — the exact
      // collapsed-builder layout this shell switch exists to prevent.
      const { container } = renderAt(path);
      await screen.findByTestId('workflow-builder', {}, { timeout: 5000 });
      const shell = container.firstElementChild as HTMLElement;
      expect(shell).toHaveClass('h-dvh');
      expect(shell).not.toHaveClass('min-h-dvh');
      expect(document.getElementById('main-content')).toHaveClass('min-h-0');
      expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
    },
  );

  test('every other route keeps the normal grow-to-content shell with the footer', () => {
    const { container } = renderAt('/architecture');
    const shell = container.firstElementChild as HTMLElement;
    expect(shell).toHaveClass('min-h-dvh');
    expect(shell).not.toHaveClass('h-dvh');
    const main = document.getElementById('main-content') as HTMLElement;
    expect(main).not.toHaveClass('min-h-0');
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
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
