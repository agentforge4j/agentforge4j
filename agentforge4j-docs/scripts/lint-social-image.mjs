// SPDX-License-Identifier: Apache-2.0
//
// Gate on every social image this module declares — the default og:image/twitter:image for the docs
// pages, and the one javadoc-seo.mjs stamps onto every published Javadoc page.
//
// Both values are deliberately ABSOLUTE URLs into the composed site root, not paths inside this
// module's own `static/`: the files they name are published by the marketing SPA
// (agentforge4j-web-ui/public/), and assemble-site.mjs composes that at the site root alongside
// /docs/ and /javadoc/. Nothing in this module's own build can therefore notice when either value
// stops naming a real file — Docusaurus does not resolve absolute URLs, javadoc-seo.mjs does not
// either, and the build would happily ship an og:image pointing at a 404 on every page. This check
// closes that gap the same way lint-navbar-logo.mjs closes the equivalent one for the navbar mark,
// and by the same means: a text-level read of each declaration (both are plain data), then a
// filesystem check against the real publishing module.
//
// It also holds each value to the size its own card type needs. The two differ on purpose and this
// check must not flatten them: Docusaurus emits `twitter:card: summary_large_image` unconditionally,
// so a square app icon there is not merely suboptimal — it is a card the renderer has no square
// layout for and will letterbox or crop. javadoc-seo.mjs declares the small `summary` card, for
// which a square icon is the correct pairing, so applying the large-card minimum there would be
// wrong. Each declaration therefore carries its own minimum and the reason for it.

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');

// Where the composed site's root-level files come from — assemble-site.mjs copies this whole
// directory to the site root, so `/brand/x.png` in a published page means this file on disk.
const SPA_PUBLIC_DIR = join(REPO_ROOT, 'agentforge4j-web-ui', 'public');

/**
 * Every place this module names a social image, with the size its declared card type requires.
 *
 * A new declaration site belongs here rather than in a second checker. The first version of this
 * file gated only `themeConfig.image` and left `DEFAULT_OG_IMAGE` — which reaches strictly more
 * published pages than the docs default does — with exactly the cross-module invisibility the whole
 * check exists to end.
 */
export const SOCIAL_IMAGE_DECLARATIONS = [
  {
    label: 'docusaurus.config.ts themeConfig.image (every docs page)',
    file: 'docusaurus.config.ts',
    declaration: 'themeConfig.image',
    pattern: /^\s*image:\s*[`'"](?:\$\{SITE_URL\}|https:\/\/[^/`'"]+)(\/[^`'"]+)[`'"]/m,
    minWidth: 1200,
    minHeight: 630,
    cardType: 'summary_large_image, which Docusaurus emits unconditionally',
  },
  {
    label: 'scripts/assemble-site.mjs DEFAULT_OG_IMAGE (every Javadoc page)',
    file: join('scripts', 'assemble-site.mjs'),
    declaration: 'DEFAULT_OG_IMAGE',
    pattern: /^\s*const\s+DEFAULT_OG_IMAGE\s*=\s*[`'"](?:\$\{DEFAULT_SITE_URL\}|https:\/\/[^/`'"]+)(\/[^`'"]+)[`'"]/m,
    minWidth: 144,
    minHeight: 144,
    cardType: 'summary, which javadoc-seo.mjs declares for these pages',
  },
];

/** The site-root-relative path a declaration names, e.g. `/brand/social-preview.png`. Accepts the
 * `${SITE_URL}` / `${DEFAULT_SITE_URL}` template forms the sources actually use as well as a plain
 * absolute URL, and returns `null` for a value that is not site-absolute at all (a
 * `static/`-relative value is Docusaurus's own business and outside this check). */
export function parseSocialImagePath(source, pattern = SOCIAL_IMAGE_DECLARATIONS[0].pattern) {
  const match = source.match(pattern);
  return match ? match[1] : null;
}

/** `{width, height}` from a PNG IHDR header, or `null` for anything that is not a PNG. */
function pngDimensions(bytes) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (bytes.length < 24 || Buffer.compare(bytes.subarray(0, 8), signature) !== 0) {
    return null;
  }
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

/** Problems with one declaration, given its source text (empty when clean). Split out so each rule
 * is directly testable against fixture sources and directories. */
export function findDeclarationProblems(declaration, source, publicDir = SPA_PUBLIC_DIR) {
  const { label, pattern, declaration: name, minWidth, minHeight, cardType } = declaration;
  const imagePath = parseSocialImagePath(source, pattern);
  if (imagePath === null) {
    return [`${label} declares no site-absolute ${name} — every page it covers would ship without one`];
  }
  const filePath = join(publicDir, ...imagePath.split('/').filter(Boolean));
  if (!existsSync(filePath)) {
    return [
      `${label} names ${imagePath}, which no file under ${publicDir} publishes — every one of those ` +
        "pages' og:image/twitter:image would point at a 404",
    ];
  }
  const dimensions = pngDimensions(readFileSync(filePath));
  if (dimensions === null) {
    // Not waved through, for the same reason verify-seo.mjs throws on the SPA's own social image:
    // skipping the size check here would leave the declared card type unverified on one half of the
    // site while the other half enforces it, holding the same class of asset to two standards.
    return [
      `${label} names ${imagePath}, which is not a PNG — this check reads dimensions from a PNG IHDR ` +
        `header only. Extend it for the new format rather than leaving a ${cardType} card unverified.`,
    ];
  }
  if (dimensions.width < minWidth || dimensions.height < minHeight) {
    return [
      `${label} is ${dimensions.width}x${dimensions.height}, under the ${minWidth}x${minHeight} its ` +
        `card type needs — it declares ${cardType}, so this renders letterboxed, cropped or rejected`,
    ];
  }
  return [];
}

/** Problems across every declaration (empty when clean). */
export function findSocialImageProblems(
  moduleRoot = MODULE_ROOT,
  publicDir = SPA_PUBLIC_DIR,
  declarations = SOCIAL_IMAGE_DECLARATIONS,
) {
  const problems = [];
  for (const declaration of declarations) {
    const sourcePath = join(moduleRoot, declaration.file);
    if (!existsSync(sourcePath)) {
      problems.push(`${declaration.label}: ${declaration.file} does not exist — this check cannot read it`);
      continue;
    }
    problems.push(...findDeclarationProblems(declaration, readFileSync(sourcePath, 'utf8'), publicDir));
  }
  return problems;
}

if (process.argv[1]?.endsWith('lint-social-image.mjs')) {
  const problems = findSocialImageProblems();
  if (problems.length > 0) {
    for (const problem of problems) {
      console.error(`lint-social-image: ${problem}`);
    }
    process.exit(1);
  }
  console.log(
    `lint-social-image: ${SOCIAL_IMAGE_DECLARATIONS.length} declared social image(s) published and sized for ` +
      'the card type each declares — clean',
  );
}
