// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
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
    ['/catalogue/some-workflow', 'Workflow catalogue'],
    ['/builder', 'Builder'],
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

  test('renders NotFoundPage for an unmatched path', () => {
    renderAt('/this-route-does-not-exist');
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

describe('branding', () => {
  test('header renders the canonical logo asset', () => {
    renderAt('/');
    const logo = screen.getByAltText('AgentForge4j');
    expect(logo).toHaveAttribute('src', '/brand/logo-horizontal.svg');
  });
});
