// SPDX-License-Identifier: Apache-2.0
//
// Client-side title/meta description/canonical sync. The static per-route HTML shells
// (scripts/build-seo.mjs) only cover the *first* request to a route — any subsequent in-app
// navigation is client-side only, so this hook (wired once in App.tsx) is what keeps the
// document's <head> honest after that.

import { beforeEach, describe, expect, test } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import App from '@/App';
import { ThemeProvider } from '@/theme/ThemeContext';
import { findSeoRoute } from '@/config/seo';
// JSON_LD_SCRIPT_ID comes from build-seo.mjs deliberately, never re-typed as a literal here: the
// static shell's script id and the one usePageSeo.ts's `setJsonLd` looks for MUST be the same
// string, and this import is the only thing in the suite that can fail when they drift. A
// hardcoded 'seo-json-ld' in these tests would keep matching usePageSeo.ts's own hardcoded copy
// long after build-seo.mjs had been renamed — leaving the shell shipping one id, the hook hunting
// for another, and every gate still green. (usePageSeo.ts itself cannot import this module —
// build-seo.mjs pulls in node:child_process — so the binding has to live here.)
// ROUTE_SCOPED_SOCIAL_TAGS is imported for the same reason and plays the same role for the social
// tags: it is the build side's own table, and the tests below assert the hook really writes every
// entry in it, with the same derivation. Re-listing those tags here would let the two surfaces
// diverge exactly as they already did once, with the suite still green.
import {
  buildSeo,
  catalogueWorkflowDescription as buildCatalogueWorkflowDescription,
  describesCompleteWords as buildDescribesCompleteWords,
  JSON_LD_SCRIPT_ID,
  MAX_DESCRIPTION_LENGTH as buildMaxDescriptionLength,
  MIN_USEFUL_DESCRIPTION_LENGTH as buildMinUsefulDescriptionLength,
  ROUTE_SCOPED_SOCIAL_TAGS,
  truncateDescription as buildTruncateDescription,
} from '../scripts/build-seo.mjs';
// The hook's own copy of the table, imported by value so the two can be compared directly rather
// than only through the tags the hook happened to write on three sampled routes.
import { ROUTE_SCOPED_SOCIAL_TAGS as HOOK_ROUTE_SCOPED_SOCIAL_TAGS } from '@/lib/usePageSeo';
import { catalogueData } from '@/lib/catalogueData';
import {
  catalogueWorkflowDescription,
  describesCompleteWords,
  MAX_DESCRIPTION_LENGTH,
  MIN_USEFUL_DESCRIPTION_LENGTH,
  truncateDescription,
} from '@/lib/catalogueSeo';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

function renderAt(path: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function canonicalHref(): string | null {
  return document.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null;
}

function metaDescription(): string | null {
  return document.querySelector('meta[name="description"]')?.getAttribute('content') ?? null;
}

function jsonLdScript(): HTMLScriptElement | null {
  return document.querySelector('script[type="application/ld+json"]');
}

function jsonLdContent(): unknown {
  const script = jsonLdScript();
  return script?.textContent ? JSON.parse(script.textContent) : null;
}

/** Renders `<App>` at `initialPath`, exposing a "navigate" button that performs a REAL client-side
 * navigation (React Router's own `useNavigate`, no remount) to `targetPath` — the only way to
 * actually reproduce a client-side-navigation-only bug: a fresh `renderAt(path)` per test mounts
 * `usePageSeo`'s effect fresh every time, which would mask a bug that only shows up when the SAME
 * mounted app instance reacts to a route change instead of being (re)constructed at the new one. */
function renderWithNavigation(initialPath: string, targetPath: string) {
  function NavigateButton() {
    const navigate = useNavigate();
    return <button onClick={() => navigate(targetPath)}>navigate</button>;
  }
  const utils = render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <NavigateButton />
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );
  return { ...utils, navigate: () => fireEvent.click(screen.getByText('navigate')) };
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
    expect(canonicalHref()).toBe('https://agentforge4j.org/architecture/');
  });

  test('/contributing canonicalizes to /community, not itself', () => {
    renderAt('/contributing');
    expect(canonicalHref()).toBe('https://agentforge4j.org/community/');
  });

  test('a real catalogue workflow id gets its own title and canonical', () => {
    renderAt('/catalogue/workflow-execution-estimator');
    expect(document.title).toBe('Workflow Execution Estimator — AgentForge4j Catalogue');
    expect(canonicalHref()).toBe('https://agentforge4j.org/catalogue/workflow-execution-estimator/');
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
    expect(canonicalHref()).toBe('https://agentforge4j.org/community/');
  });

  test('a differently-cased real static route still resolves to that route, not Home', () => {
    renderAt('/Community');
    expect(document.title).toBe('Community & Contributing — AgentForge4j');
    expect(canonicalHref()).toBe('https://agentforge4j.org/community/');
  });

  test('a trailing slash on a real catalogue detail route still resolves to that workflow, not Home', () => {
    renderAt('/catalogue/workflow-execution-estimator/');
    expect(document.title).toBe('Workflow Execution Estimator — AgentForge4j Catalogue');
    expect(canonicalHref()).toBe('https://agentforge4j.org/catalogue/workflow-execution-estimator/');
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
    const buildTimeCanonical = sitemapUrls.find((url) => url.endsWith(`/catalogue/${realId}/`));
    expect(buildTimeCanonical).toBe(`https://agentforge4j.org/catalogue/${realId}/`);

    renderAt(`/catalogue/${realId}`);
    expect(canonicalHref()).toBe(buildTimeCanonical);
  });

  // --- JSON-LD: kept in sync with the current route, including across a client-side navigation
  // that never re-fetches the build-time static shell (scripts/build-seo.mjs's `injectJsonLd`
  // only ever covers the first request to a route). ---

  test('sets the home jsonLd (matching the real seo-routes.json config) on a fresh load of "/"', () => {
    renderAt('/');
    expect(jsonLdScript()?.getAttribute('type')).toBe('application/ld+json');
    expect(jsonLdContent()).toEqual(findSeoRoute('/')?.jsonLd);
  });

  test('the script the hook CREATES carries the exact id build-seo.mjs stamps on the static shell, so the two can never drift apart silently', () => {
    // The one assertion that actually binds usePageSeo.ts's own id to build-seo.mjs's exported
    // constant. Every other guard on this invariant lives on the build side (build-seo.test.mjs,
    // verify-seo.mjs) and compares the shell against that same constant — so all of them stay
    // green if the constant is renamed, while production ships a shell the hook can no longer
    // find, appends a SECOND JSON-LD block on `/`, and then strands the shell's block on every
    // later route. This test is what fails first in that scenario.
    document.querySelectorAll('script[type="application/ld+json"]').forEach((el) => el.remove());
    renderAt('/');
    expect(jsonLdScript()?.id).toBe(JSON_LD_SCRIPT_ID);
  });

  test('a static route that declares no jsonLd has no JSON-LD script at all', () => {
    renderAt('/architecture');
    expect(jsonLdScript()).toBeNull();
  });

  test('a real catalogue workflow detail page has no JSON-LD script (no workflow declares one today)', () => {
    renderAt('/catalogue/workflow-execution-estimator');
    expect(jsonLdScript()).toBeNull();
  });

  // The static 404.html shell carries NO JSON-LD of its own: copy-404.mjs takes it from the built
  // index.html *before* build-seo.mjs injects anything, so unlike title/canonical — which that
  // pre-injection shell already happens to carry in their home form — structured data on an
  // unmatched path exists only because this hook adds it. Do not read this fallback as redundant
  // with the shell; removing it would leave every unmatched path with no structured data at all.
  test('an unmatched path gets home\'s jsonLd added client-side — the static 404.html shell ships with none of its own', () => {
    renderAt('/this-route-does-not-exist');
    expect(jsonLdContent()).toEqual(findSeoRoute('/')?.jsonLd);
  });

  test('a client-side navigation from a route with no jsonLd to "/" adds it, without a fresh page load', () => {
    const { navigate } = renderWithNavigation('/architecture', '/');
    expect(jsonLdScript()).toBeNull();

    navigate();

    expect(jsonLdContent()).toEqual(findSeoRoute('/')?.jsonLd);
  });

  test('a client-side navigation away from "/" removes its jsonLd, rather than leaving it stale on the new route', () => {
    const { navigate } = renderWithNavigation('/', '/architecture');
    expect(jsonLdContent()).toEqual(findSeoRoute('/')?.jsonLd);

    navigate();

    expect(jsonLdScript()).toBeNull();
  });

  // --- Reproduces the real browser boot sequence: the static shell (scripts/build-seo.mjs's
  // injectJsonLd) already has a JSON-LD <script id={JSON_LD_SCRIPT_ID}> in <head> BEFORE React ever
  // mounts — unlike every test above, which starts from an empty document.head and lets the hook
  // create the script itself. Both fixtures below stamp build-seo.mjs's own exported constant
  // rather than a re-typed literal, so they model the real shell rather than a hand-copied
  // approximation of it. A regression that gave the static shell a different id (or none) would
  // make the hook fail to find it here, create a *second* script instead of adopting this one, and
  // then only ever remove that second one on navigation — permanently stranding this one. ---

  test('adopts and updates a pre-existing static-shell JSON-LD script (same shared id) on mount, rather than creating a duplicate', () => {
    document.querySelectorAll('script[type="application/ld+json"]').forEach((el) => el.remove());
    const staticScript = document.createElement('script');
    staticScript.id = JSON_LD_SCRIPT_ID;
    staticScript.setAttribute('type', 'application/ld+json');
    staticScript.textContent = JSON.stringify(findSeoRoute('/')?.jsonLd);
    document.head.appendChild(staticScript);

    renderAt('/');

    const scripts = document.head.querySelectorAll('script[type="application/ld+json"]');
    expect(scripts.length).toBe(1);
    expect(scripts[0]).toBe(staticScript);
    expect(jsonLdContent()).toEqual(findSeoRoute('/')?.jsonLd);
  });

  test('a pre-existing static-shell JSON-LD script is fully removed (not stranded) after a client-side navigation away from "/"', () => {
    document.querySelectorAll('script[type="application/ld+json"]').forEach((el) => el.remove());
    const staticScript = document.createElement('script');
    staticScript.id = JSON_LD_SCRIPT_ID;
    staticScript.setAttribute('type', 'application/ld+json');
    staticScript.textContent = JSON.stringify(findSeoRoute('/')?.jsonLd);
    document.head.appendChild(staticScript);

    const { navigate } = renderWithNavigation('/', '/architecture');
    navigate();

    expect(document.head.querySelectorAll('script[type="application/ld+json"]').length).toBe(0);
  });
});

// --- Route-scoped Open Graph / Twitter sync. The audited defect: the static shell rewrote all five
// of these per route while the hook rewrote none, so every one of them went stale the moment a
// visitor navigated inside the app — with title, description and canonical updating correctly
// around them, which is why nothing looked obviously broken. ---

function metaContent(attribute: string, key: string): string | null {
  return document.querySelector(`meta[${attribute}="${key}"]`)?.getAttribute('content') ?? null;
}

function metaCount(attribute: string, key: string): number {
  return document.querySelectorAll(`meta[${attribute}="${key}"]`).length;
}

/** The three values every route-scoped social tag is derived from, read back off the document. */
function derivedSources(): Record<string, string | null> {
  return { title: document.title, description: metaDescription(), canonical: canonicalHref() };
}

describe('usePageSeo route-scoped social metadata', () => {
  // jsdom's `document` is shared across the tests in this file and Testing Library's cleanup only
  // unmounts components — anything the hook wrote into <head> survives into the next test. The
  // JSON-LD tests above already clear their own script for the same reason; the tag-COUNT
  // assertions below are only meaningful against a head this test put into a known state.
  beforeEach(() => {
    for (const { attribute, key } of ROUTE_SCOPED_SOCIAL_TAGS) {
      document.querySelectorAll(`meta[${attribute}="${key}"]`).forEach((el) => el.remove());
    }
  });

  // The binding between the two copies of the table, stated as an equality so it holds in BOTH
  // directions. The per-route assertions below prove the hook writes everything the build writes;
  // only this proves the hook writes NOTHING MORE. A tag present in the hook's table and absent
  // from build-seo.mjs's would ship to JavaScript-executing consumers only — missing from every
  // static shell a crawler receives — and neither verify-seo.mjs (which states its own required
  // list) nor verify-client-nav-seo.mjs (whose two sides are both hook-written, so they converge)
  // would see it.
  test('the hook\'s route-scoped table and the build\'s are element-for-element equal', () => {
    expect(HOOK_ROUTE_SCOPED_SOCIAL_TAGS).toEqual(ROUTE_SCOPED_SOCIAL_TAGS);
    // Non-vacuity: two empty tables would satisfy the equality and check nothing.
    expect(HOOK_ROUTE_SCOPED_SOCIAL_TAGS.length).toBeGreaterThan(0);
  });

  // Every entry in the build's table is asserted against what the hook really wrote. Adding a sixth
  // tag on the build side without teaching the hook about it fails here — which is precisely the
  // drift that produced the audited defect, just in the other direction.
  test.each(['/', '/api', '/architecture'])(
    'every tag the static shell rewrites for a route is also written client-side, with the same derivation (%s)',
    (path) => {
      renderAt(path);
      const sources = derivedSources();
      expect(ROUTE_SCOPED_SOCIAL_TAGS.length).toBeGreaterThan(0);
      for (const { attribute, key, source } of ROUTE_SCOPED_SOCIAL_TAGS) {
        expect(metaCount(attribute, key), `${key} should exist exactly once`).toBe(1);
        expect(metaContent(attribute, key), `${key} should equal the page's ${source}`).toBe(sources[source]);
      }
    },
  );

  test('a client-side navigation A -> B leaves every social tag describing B, not A', () => {
    const { navigate } = renderWithNavigation('/', '/architecture');
    expect(metaContent('property', 'og:title')).toBe('AgentForge4j — Governed AI Workflows for Java');
    navigate();
    expect(document.title).toBe('Architecture — AgentForge4j');
    expect(metaContent('property', 'og:title')).toBe('Architecture — AgentForge4j');
    expect(metaContent('property', 'og:url')).toBe('https://agentforge4j.org/architecture/');
    expect(metaContent('name', 'twitter:title')).toBe('Architecture — AgentForge4j');
    expect(metaContent('property', 'og:description')).toBe(metaDescription());
    expect(metaContent('name', 'twitter:description')).toBe(metaDescription());
  });

  test('the reverse navigation B -> A is equally correct — the sync is not one-directional', () => {
    const { navigate } = renderWithNavigation('/architecture', '/');
    expect(metaContent('property', 'og:title')).toBe('Architecture — AgentForge4j');
    navigate();
    expect(metaContent('property', 'og:title')).toBe('AgentForge4j — Governed AI Workflows for Java');
    expect(metaContent('property', 'og:url')).toBe('https://agentforge4j.org/');
  });

  test('navigating to a catalogue detail route carries that workflow\'s own social metadata', () => {
    const workflow = catalogueData.workflows[0];
    const { navigate } = renderWithNavigation('/', `/catalogue/${workflow.id}`);
    navigate();
    expect(metaContent('property', 'og:title')).toBe(document.title);
    expect(metaContent('property', 'og:url')).toBe(`https://agentforge4j.org/catalogue/${workflow.id}/`);
  });

  test('an unknown route\'s social tags agree with whatever fallback metadata it resolved to, never a stale previous route', () => {
    const { navigate } = renderWithNavigation('/architecture', '/no-such-page');
    navigate();
    const sources = derivedSources();
    for (const { attribute, key, source } of ROUTE_SCOPED_SOCIAL_TAGS) {
      expect(metaContent(attribute, key)).toBe(sources[source]);
    }
    expect(metaContent('property', 'og:title')).not.toBe('Architecture — AgentForge4j');
  });

  test('adopts and UPDATES the static shell\'s own social tags rather than appending duplicates beside them', () => {
    // The real first-request condition: build-seo.mjs already wrote these into the shell's <head>
    // before React ever ran. A hook that appended would leave two contradictory og:title tags, and
    // a crawler reading the first would get the value from the initially-loaded route forever.
    const shellTag = document.createElement('meta');
    shellTag.setAttribute('property', 'og:title');
    shellTag.setAttribute('content', 'stale shell value');
    document.head.appendChild(shellTag);

    const { navigate } = renderWithNavigation('/', '/architecture');
    navigate();
    expect(metaCount('property', 'og:title')).toBe(1);
    expect(metaContent('property', 'og:title')).toBe('Architecture — AgentForge4j');
  });

  test('repeated navigation around the same routes never accumulates tags', () => {
    const { navigate } = renderWithNavigation('/', '/architecture');
    navigate();
    navigate();
    navigate();
    for (const { attribute, key } of ROUTE_SCOPED_SOCIAL_TAGS) {
      expect(metaCount(attribute, key), `${key} accumulated`).toBe(1);
    }
  });
});

// --- Catalogue description truncation: the TS implementation the client renders from and the
// plain-ESM one the build shell is written from are deliberate duplicates (neither can import the
// other). This is what binds them, and it is the ONLY thing that does — the rule is far too big to
// keep in sync by eye. Every duplicated unit is driven in both implementations, over the REAL
// shipped workflow data AND over a corpus of hard cases the real data does not happen to contain,
// and must agree exactly. A fix applied to one copy and forgotten in the other fails here instead
// of shipping two different meta descriptions for the same page.
//
// The corpus matters as much as the real data: with only two shipped workflows, a divergence that
// does not change those two specific strings would otherwise ship unobserved.

const BINDING_CORPUS: readonly string[] = [
  'A short, complete description.',
  'x'.repeat(MAX_DESCRIPTION_LENGTH),
  'x'.repeat(MAX_DESCRIPTION_LENGTH + 1),
  'x'.repeat(400),
  ','.repeat(300),
  `Ok. ${'Alpha beta gamma delta '.repeat(20)}`,
  `${'word '.repeat(28)}alpha, beta gamma delta epsilon zeta eta theta iota kappa lambda`,
  `${'alpha\nbeta\ngamma\n'.repeat(20)}delta`,
  // The `e.g.`/`...` entries deliberately place the terminator ABOVE MIN_USEFUL_DESCRIPTION_LENGTH
  // and follow it with a capital: below the floor, or before a lower-case word, something other
  // than the dotted-word rule explains the result and the entry stops binding that rule at all.
  'Runs adapters over several transports for governed workflow execution and delivery to each ' +
    'configured provider, e.g. HTTP, gRPC and in-process, chosen per agent and per step of the run.',
  'Estimates token range, agent turns, tool invocations, structural risk flags, etc. and then ' +
    'returns a continue, narrow or stop recommendation for the caller to act on before executing.',
  'A governed workflow summary with a long lead-in clause that eventually trails off and then ' +
    'stops mid thought... And afterwards it continues for a good while longer than the budget allows.',
  `A governed workflow summary that stops mid thought... ${'x'.repeat(200)}`,
  `Runs adapters for governed AI workflow execution, e.g. ${'y'.repeat(200)}`,
  'Supports the governed workflow contract as shipped in release 1.0. The rest of this sentence ' +
    'exists only to push the text past the budget so the rule has something to shorten.',
];

describe('catalogue description truncation', () => {
  test('both thresholds are one number each, not two — the TS copies match the build copies', () => {
    expect(MAX_DESCRIPTION_LENGTH).toBe(buildMaxDescriptionLength);
    expect(MIN_USEFUL_DESCRIPTION_LENGTH).toBe(buildMinUsefulDescriptionLength);
  });

  test('the corpus really exercises the sentence rule — every abbreviation entry sits above the useful-length floor', () => {
    // Without this, a reworded corpus entry can drop its terminator below the floor and quietly
    // stop binding the rule it was added for, while every test still passes. That is not
    // hypothetical: it is how the `e.g.` binding was lost once, unnoticed by a green suite.
    for (const terminator of ['e.g.', 'thought...']) {
      const entry = BINDING_CORPUS.find((raw) => raw.includes(terminator));
      expect(entry, terminator).toBeDefined();
      const end = entry!.indexOf(terminator) + terminator.length;
      expect(end, `${terminator} ends at ${end}`).toBeGreaterThanOrEqual(MIN_USEFUL_DESCRIPTION_LENGTH);
      expect(end, `${terminator} ends at ${end}`).toBeLessThanOrEqual(MAX_DESCRIPTION_LENGTH);
      expect(entry!.slice(end)).toMatch(/^\s+\p{Lu}/u);
    }
  });

  test('the client-side and build-time implementations produce identical descriptions for every real shipped workflow, fallback included', () => {
    expect(catalogueData.workflows.length).toBeGreaterThan(0);
    for (const workflow of catalogueData.workflows) {
      // Bound against the build's OWN entry point, not its truncation rule: that is what covers
      // the no-description fallback sentence, which `truncateDescription` never sees. A workflow
      // with no description is valid (`description` is optional in workflow.schema.json), and
      // comparing against the rule alone would fail on it while both copies behaved correctly.
      expect(catalogueWorkflowDescription(workflow), workflow.id).toBe(
        buildCatalogueWorkflowDescription(workflow),
      );
    }
  });

  test('a workflow with no description is bound too — both copies emit the same fallback sentence', () => {
    const workflow = { id: 'no-description', name: 'No Description', description: null };
    expect(catalogueWorkflowDescription(workflow)).toBe(buildCatalogueWorkflowDescription(workflow));
    expect(catalogueWorkflowDescription(workflow)).toContain('ready-to-run AgentForge4j workflow');
  });

  test('both truncation implementations agree on every case in the shared corpus, not only on the two shipped strings', () => {
    for (const raw of BINDING_CORPUS) {
      expect(truncateDescription(raw), JSON.stringify(raw.slice(0, 40))).toBe(buildTruncateDescription(raw));
    }
  });

  test('both validator implementations agree on every case in the shared corpus, and on the shapes the rule must reject', () => {
    for (const raw of BINDING_CORPUS) {
      const description = truncateDescription(raw);
      expect(describesCompleteWords(raw, description), JSON.stringify(description)).toBe(
        buildDescribesCompleteWords(raw, description),
      );
    }
    // Rejection shapes, so the binding covers `false` results and not only `true` ones.
    const source = 'token range, agent turns, tool invocations, complexity';
    for (const candidate of ['token range, agent turns, tool invoc…', 'alpha delta…', '…', source]) {
      expect(describesCompleteWords(source, candidate), candidate).toBe(
        buildDescribesCompleteWords(source, candidate),
      );
    }
  });

  test('every real shipped workflow ends on a complete word, within the limit — and none reproduces the mid-word cut this rule replaced', () => {
    for (const workflow of catalogueData.workflows) {
      const raw = (workflow.description ?? '').trim();
      const description = catalogueWorkflowDescription(workflow);
      expect(description.length, workflow.id).toBeLessThanOrEqual(MAX_DESCRIPTION_LENGTH);
      if (raw) {
        expect(describesCompleteWords(raw, description), `${workflow.id}: ${description}`).toBe(true);
      }
      // The two descriptions this site actually published, stated as the properties they violated.
      expect(description).not.toMatch(/\bSin…$/);
      expect(description).not.toMatch(/\binvoc…$/);
    }
  });
});
