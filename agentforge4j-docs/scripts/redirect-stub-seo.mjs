// SPDX-License-Identifier: Apache-2.0
//
// SEO for the composed artifact's client-redirect stubs — the pages
// `@docusaurus/plugin-client-redirects` writes for `/docs/` and `/docs/latest/`.
//
// Raw, those stubs are the thinnest possible HTML: a meta refresh, a RELATIVE canonical, a script
// that sets `window.location.href`, and nothing else — no `<title>`, no description, no robots
// directive, not even a `<body>`. Published on a static host they answer 200, which makes each one
// an indexable, title-less, near-empty page sitting at the site's most linked-to documentation
// address. `/docs/` in particular was the ONLY route into the documentation tree, so it was also the
// page most likely to be crawled first.
//
// This pass leaves the redirect behaviour byte-for-byte intact and adds the metadata that makes the
// page describe itself honestly:
//
//   - a real `<title>` and description, so it is not a blank result in an index or a share preview;
//   - `robots: noindex, follow` — the page has no content of its own to index, and `follow` keeps
//     the destination link crawlable so the redirect still passes signal through;
//   - an ABSOLUTE canonical pointing at the destination, replacing the plugin's relative one. A
//     relative canonical is legal but resolves against whatever URL the crawler thinks it is on;
//     every other canonical this site publishes is absolute, and a redirect shell's canonical
//     naming its destination is the standard "the real content is over there" signal.
//
// What it deliberately does NOT do: pretend to be an HTTP redirect. GitHub Pages (see
// .github/workflows/deploy.yml) serves static files with no redirect configuration of any kind, so
// a 301 from `/docs/` is not implementable on this host. The marketing site's own links are pointed
// straight at the destination instead (agentforge4j-web-ui/scripts/build-docs-entry.mjs), leaving
// these stubs to serve only the inbound links that already exist.

const REFRESH_PATTERN = /<meta\s+http-equiv="refresh"\s+content="0;\s*url=([^"]+)"\s*\/?>/i;
const RELATIVE_CANONICAL_PATTERN = /<link\s+rel="canonical"\s+href="([^"]+)"\s*\/?>/i;

/** The destination a client-redirect stub points at, or `null` when `html` is not one. Recognized
 * by the meta refresh itself rather than by filename: which paths the plugin generates a stub for
 * is derived from the version lifecycle (docusaurus.config.ts's `redirectConfig`) and changes at a
 * release cut, so a hardcoded list of stub paths would silently stop covering them. */
export function redirectStubTarget(html) {
  const match = REFRESH_PATTERN.exec(html);
  return match ? match[1] : null;
}

/**
 * Rewrites one client-redirect stub's `<head>`. The meta refresh, the redirect script and the body
 * are untouched — this only adds the metadata the stub never had and absolutizes the canonical it
 * did.
 *
 * Idempotent by refusal, matching javadoc-seo.mjs's own style: a stub that already carries a robots
 * directive is returned unchanged rather than accumulating a second one, so re-running this over an
 * already-processed artifact cannot emit contradictory directives.
 *
 * @param {string} html
 * @param {{siteUrl: string, title: string, description: string, robots: string}} options
 */
export function injectRedirectStubSeo(html, { siteUrl, title, description, robots }) {
  const target = redirectStubTarget(html);
  if (target === null) {
    throw new Error('redirect-stub-seo: not a client-redirect stub — no meta refresh found');
  }
  if (/<meta\s+name="robots"/i.test(html)) {
    return html;
  }
  if (!/<\/head>/i.test(html)) {
    throw new Error('redirect-stub-seo: expected a </head> closing tag — template drift?');
  }

  const absoluteTarget = target.startsWith('http') ? target : `${siteUrl}${target}`;
  let result = html;

  // The plugin's own relative canonical is replaced, not supplemented — two canonical links on one
  // page is worse than the relative one alone.
  if (RELATIVE_CANONICAL_PATTERN.test(result)) {
    result = result.replace(
      RELATIVE_CANONICAL_PATTERN,
      () => `<link rel="canonical" href="${escapeHtmlAttribute(absoluteTarget)}">`,
    );
  } else {
    result = result.replace(
      /<\/head>/i,
      () => `<link rel="canonical" href="${escapeHtmlAttribute(absoluteTarget)}">\n</head>`,
    );
  }

  const added =
    `<title>${escapeHtmlText(title)}</title>\n` +
    `<meta name="description" content="${escapeHtmlAttribute(description)}">\n` +
    `<meta name="robots" content="${escapeHtmlAttribute(robots)}">\n`;
  return result.replace(/<\/head>/i, () => `${added}</head>`);
}

// Function replacers throughout above (never a bare replacement string): a value containing `$&`,
// "$`" or `$'` would otherwise be read as a substitution pattern and silently corrupt the output —
// the same guard build-seo.mjs and javadoc-seo.mjs already document.
function escapeHtmlAttribute(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeHtmlText(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
