// SPDX-License-Identifier: Apache-2.0
//
// Client-side title/meta description/canonical sync. The static per-route HTML shells
// (scripts/build-seo.mjs) only cover the *first* request to a route — any subsequent in-app
// navigation is client-side only, so this hook (wired once in App.tsx) is what keeps the
// document's <head> honest after that.

import { describe, expect, test } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '@/App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

function canonicalHref(): string | null {
  return document.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null;
}

function metaDescription(): string | null {
  return document.querySelector('meta[name="description"]')?.getAttribute('content') ?? null;
}

describe('usePageSeo', () => {
  test('sets the home title/description/canonical on "/"', () => {
    renderAt('/');
    expect(document.title).toBe('AgentForge4j — Governed AI Workflows for Java');
    expect(metaDescription()).toMatch(/^AgentForge4j is an open-source Java framework/);
    expect(canonicalHref()).toBe('https://agentforge4j.org/');
  });

  test('sets a distinct title/canonical for a static route', () => {
    renderAt('/architecture');
    expect(document.title).toBe('Architecture — AgentForge4j');
    expect(canonicalHref()).toBe('https://agentforge4j.org/architecture');
  });

  test('/contributing canonicalizes to /community, not itself', () => {
    renderAt('/contributing');
    expect(canonicalHref()).toBe('https://agentforge4j.org/community');
  });

  test('a real catalogue workflow id gets its own title and canonical', () => {
    renderAt('/catalogue/workflow-execution-estimator');
    expect(document.title).toBe('Workflow Execution Estimator — AgentForge4j Catalogue');
    expect(canonicalHref()).toBe('https://agentforge4j.org/catalogue/workflow-execution-estimator');
  });

  test('an unmatched path falls back to the home title/canonical rather than a stale value', () => {
    renderAt('/this-route-does-not-exist');
    expect(document.title).toBe('AgentForge4j — Governed AI Workflows for Java');
    expect(canonicalHref()).toBe('https://agentforge4j.org/');
  });
});
