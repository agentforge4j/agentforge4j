// SPDX-License-Identifier: Apache-2.0
//
// Client-side title/meta description/canonical sync. The static per-route HTML shells
// (scripts/build-seo.mjs) only cover the *first* request to a route — any subsequent in-app
// navigation is client-side only, so this hook (wired once in App.tsx) is what keeps the
// document's <head> honest after that.

import { describe, expect, test } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import App from '@/App';
import { buildSeo } from '../scripts/build-seo.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

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

  // React Router's own default matching is case-insensitive and tolerates an optional trailing
  // slash — every case below is a URL the app itself renders the *correct* page for. The SEO
  // metadata must agree with what actually rendered rather than falling back to Home, and the
  // canonical emitted must always be the one clean, normalized address regardless of which variant
  // was visited (never a duplicate, trailing-slash/case-preserving echo of the input).

  test('a trailing slash on a real static route still resolves to that route, not Home', () => {
    renderAt('/community/');
    expect(document.title).toBe('Community & Contributing — AgentForge4j');
    expect(canonicalHref()).toBe('https://agentforge4j.org/community');
  });

  test('a differently-cased real static route still resolves to that route, not Home', () => {
    renderAt('/Community');
    expect(document.title).toBe('Community & Contributing — AgentForge4j');
    expect(canonicalHref()).toBe('https://agentforge4j.org/community');
  });

  test('a trailing slash on a real catalogue detail route still resolves to that workflow, not Home', () => {
    renderAt('/catalogue/workflow-execution-estimator/');
    expect(document.title).toBe('Workflow Execution Estimator — AgentForge4j Catalogue');
    expect(canonicalHref()).toBe('https://agentforge4j.org/catalogue/workflow-execution-estimator');
  });

  test('an unknown route with a trailing slash still falls back to Home, not a stale value', () => {
    renderAt('/this-route-does-not-exist/');
    expect(document.title).toBe('AgentForge4j — Governed AI Workflows for Java');
    expect(canonicalHref()).toBe('https://agentforge4j.org/');
  });

  test('an unknown catalogue workflow id uses NotFound (= Home) metadata, not stale or fabricated metadata', () => {
    // /catalogue/:id matches the route shape (CatalogueDetailPage renders), but no real workflow
    // has this id, so CatalogueDetailPage itself renders NotFoundPage — this app has no metadata
    // distinct from Home for "not found" (404.html is byte-identical to the home shell by design),
    // so falling back to Home's title/canonical here is the correct "NotFound metadata", not a bug.
    renderAt('/catalogue/this-workflow-id-does-not-exist');
    expect(document.title).toBe('AgentForge4j — Governed AI Workflows for Java');
    expect(canonicalHref()).toBe('https://agentforge4j.org/');
  });

  test('build-time canonical (build-seo.mjs) and client-side canonical (usePageSeo) agree for a real shipped catalogue workflow id', () => {
    const root = mkdtempSync(join(tmpdir(), 'canon-consistency-'));
    const distDir = join(root, 'dist');
    mkdirSync(distDir, { recursive: true });
    writeFileSync(join(distDir, 'index.html'), readFileSync(join(MODULE_ROOT, 'index.html'), 'utf8'), 'utf8');

    const { sitemapUrls } = buildSeo({
      distDir,
      seoRoutesPath: join(MODULE_ROOT, 'src/config/seo-routes.json'),
      catalogueDataPath: join(MODULE_ROOT, 'src/generated/catalogue-data.json'),
    });

    const realId = 'agent-creator';
    const buildTimeCanonical = sitemapUrls.find((url) => url.endsWith(`/catalogue/${realId}`));
    expect(buildTimeCanonical).toBe(`https://agentforge4j.org/catalogue/${realId}`);

    renderAt(`/catalogue/${realId}`);
    expect(canonicalHref()).toBe(buildTimeCanonical);
  });
});
