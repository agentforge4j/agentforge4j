// SPDX-License-Identifier: Apache-2.0
//
// Generates two things from the already-built dist/index.html (run after `vite build`, before
// copy-404.mjs so 404.html stays byte-identical to the *final* index.html):
//
//  1. A per-route static HTML shell for every real SPA route (dist/<route>/index.html) with
//     <title>/<meta description>/<link canonical> (and matching OG/Twitter tags) baked in as
//     real static text. This is a pure client-rendered SPA with no SSR/prerendering — page
//     *content* stays entirely client-rendered once the bundle loads; only the <head> metadata
//     in the initial static response differs per route. See the SEO grounding doc
//     (designs/In progress/seo-sitemap-canonicals/) for why this is the chosen approach.
//  2. This module's own sitemap.xml fragment: real absolute HTTPS agentforge4j.org URLs for
//     every route in seo-routes.json marked `sitemap: true` (the default), plus one per real
//     shipped catalogue workflow (src/generated/catalogue-data.json). assemble-site.mjs
//     (agentforge4j-docs) merges this fragment with the Docusaurus-generated docs/sitemap.xml
//     into the one final sitemap.xml at the composed site root — this script knows nothing
//     about docs pages, and assemble-site.mjs knows nothing about SPA routes.
//
// Per-workflow title/description formatting mirrors src/lib/catalogueSeo.ts (used by the
// client-side title/meta sync, usePageSeo) — duplicated deliberately, not imported, because this
// is plain Node ESM with no bundler step ahead of it; kept to two small, easy-to-eyeball rules.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

const DIST_DIR = join(MODULE_ROOT, 'dist');
const SEO_ROUTES_PATH = join(MODULE_ROOT, 'src', 'config', 'seo-routes.json');
const CATALOGUE_DATA_PATH = join(MODULE_ROOT, 'src', 'generated', 'catalogue-data.json');

const MAX_DESCRIPTION_LENGTH = 157; // mirrors src/lib/catalogueSeo.ts

function escapeHtml(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function catalogueWorkflowTitle(workflow) {
  return `${workflow.name} — AgentForge4j Catalogue`;
}

function catalogueWorkflowDescription(workflow) {
  const raw = workflow.description?.trim();
  if (!raw) {
    return `${workflow.name} — a shipped, ready-to-run AgentForge4j workflow from the workflow catalogue.`;
  }
  if (raw.length <= MAX_DESCRIPTION_LENGTH) {
    return raw;
  }
  return `${raw.slice(0, MAX_DESCRIPTION_LENGTH - 1).trimEnd()}…`;
}

/** Replaces the title/description/canonical/OG/Twitter tags already present in the built
 * index.html shell — never adds new tags, so a template drift (a tag renamed/removed from
 * index.html) fails loudly here instead of silently no-op'ing. */
function injectHead(html, { title, description, canonical }) {
  const safeTitle = escapeHtml(title);
  const safeDescription = escapeHtml(description);
  const replacements = [
    [/<title>[\s\S]*?<\/title>/, `<title>${safeTitle}</title>`],
    [/<meta\s+name="description"[\s\S]*?\/>/, `<meta name="description" content="${safeDescription}" />`],
    [/<link\s+rel="canonical"[\s\S]*?\/>/, `<link rel="canonical" href="${canonical}" />`],
    [/<meta\s+property="og:title"[\s\S]*?\/>/, `<meta property="og:title" content="${safeTitle}" />`],
    [
      /<meta\s+property="og:description"[\s\S]*?\/>/,
      `<meta property="og:description" content="${safeDescription}" />`,
    ],
    [/<meta\s+property="og:url"[\s\S]*?\/>/, `<meta property="og:url" content="${canonical}" />`],
    [/<meta\s+name="twitter:title"[\s\S]*?\/>/, `<meta name="twitter:title" content="${safeTitle}" />`],
    [
      /<meta\s+name="twitter:description"[\s\S]*?\/>/,
      `<meta name="twitter:description" content="${safeDescription}" />`,
    ],
  ];

  let result = html;
  for (const [pattern, replacement] of replacements) {
    if (!pattern.test(result)) {
      throw new Error(`build-seo: expected tag not found in dist/index.html: ${pattern}`);
    }
    result = result.replace(pattern, replacement);
  }
  return result;
}

function writeShell(distDir, routePath, html) {
  if (routePath === '/') {
    writeFileSync(join(distDir, 'index.html'), html, 'utf8');
    return;
  }
  const dir = join(distDir, ...routePath.split('/').filter(Boolean));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'index.html'), html, 'utf8');
}

function sitemapXml(urls) {
  const body = urls.map((url) => `  <url>\n    <loc>${escapeHtml(url)}</loc>\n  </url>`).join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

/**
 * @param {{distDir?: string, seoRoutesPath?: string, catalogueDataPath?: string}} [options]
 * @returns {{shellsWritten: number, sitemapUrls: string[]}}
 */
export function buildSeo({
  distDir = DIST_DIR,
  seoRoutesPath = SEO_ROUTES_PATH,
  catalogueDataPath = CATALOGUE_DATA_PATH,
} = {}) {
  const indexPath = join(distDir, 'index.html');
  if (!existsSync(indexPath)) {
    throw new Error(`build-seo: ${indexPath} does not exist — run "vite build" first`);
  }
  const baseHtml = readFileSync(indexPath, 'utf8');

  const { siteUrl, routes } = JSON.parse(readFileSync(seoRoutesPath, 'utf8'));
  const catalogueData = existsSync(catalogueDataPath)
    ? JSON.parse(readFileSync(catalogueDataPath, 'utf8'))
    : { workflows: [] };

  const sitemapUrls = [];
  let shellsWritten = 0;

  for (const route of routes) {
    const canonicalPath = route.canonicalPath ?? route.path;
    const canonical = canonicalPath === '/' ? `${siteUrl}/` : `${siteUrl}${canonicalPath}`;
    const html = injectHead(baseHtml, { title: route.title, description: route.description, canonical });
    writeShell(distDir, route.path, html);
    shellsWritten += 1;
    if (route.sitemap !== false) {
      sitemapUrls.push(route.path === '/' ? `${siteUrl}/` : `${siteUrl}${route.path}`);
    }
  }

  for (const workflow of catalogueData.workflows ?? []) {
    const routePath = `/catalogue/${workflow.id}`;
    const canonical = `${siteUrl}${routePath}`;
    const html = injectHead(baseHtml, {
      title: catalogueWorkflowTitle(workflow),
      description: catalogueWorkflowDescription(workflow),
      canonical,
    });
    writeShell(distDir, routePath, html);
    shellsWritten += 1;
    sitemapUrls.push(canonical);
  }

  const uniqueUrls = [...new Set(sitemapUrls)];
  if (uniqueUrls.length !== sitemapUrls.length) {
    throw new Error(
      'build-seo: duplicate URL(s) computed for the sitemap fragment — check seo-routes.json/catalogue ids',
    );
  }

  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml(uniqueUrls), 'utf8');

  return { shellsWritten, sitemapUrls: uniqueUrls };
}

function main() {
  const result = buildSeo();
  console.log(
    `[build-seo] wrote ${result.shellsWritten} route shell(s), ${result.sitemapUrls.length} sitemap URL(s)`,
  );
}

if (process.argv[1]?.endsWith('build-seo.mjs')) {
  main();
}
