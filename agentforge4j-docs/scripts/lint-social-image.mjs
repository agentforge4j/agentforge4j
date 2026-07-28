// SPDX-License-Identifier: Apache-2.0
//
// Gate on `themeConfig.image` in docusaurus.config.ts — the default og:image/twitter:image for every
// docs page.
//
// The value is deliberately an ABSOLUTE URL into the composed site root, not a path inside this
// module's own `static/`: the file it names is published by the marketing SPA
// (agentforge4j-web-ui/public/), and assemble-site.mjs composes that at the site root alongside
// /docs/. Nothing in this module's own build can therefore notice when the value stops naming a real
// file — Docusaurus does not resolve absolute URLs, and the docs build would happily ship an
// og:image pointing at a 404 on every page. This check closes that gap the same way
// lint-navbar-logo.mjs closes the equivalent one for the navbar mark, and by the same means: a
// text-level read of the config (it is plain data), then a filesystem check against the real
// publishing module.
//
// It also holds the value to the size a large summary card needs. Docusaurus emits
// `twitter:card: summary_large_image` unconditionally, so a square app icon here is not merely
// suboptimal — it is a card the renderer has no square layout for and will letterbox or crop.

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');
const CONFIG_PATH = join(MODULE_ROOT, 'docusaurus.config.ts');

// Where the composed site's root-level files come from — assemble-site.mjs copies this whole
// directory to the site root, so `/brand/x.png` in a published page means this file on disk.
const SPA_PUBLIC_DIR = join(REPO_ROOT, 'agentforge4j-web-ui', 'public');

const MIN_LARGE_CARD_WIDTH = 1200;
const MIN_LARGE_CARD_HEIGHT = 630;

/** The site-root-relative path `themeConfig.image` names, e.g. `/brand/social-preview.png`.
 * Accepts the `${SITE_URL}/...` template form the config actually uses as well as a plain absolute
 * URL, and returns `null` for a value that is not site-absolute at all (a `static/`-relative value
 * is Docusaurus's own business and outside this check). */
export function parseSocialImagePath(configSource) {
  const match = configSource.match(/^\s*image:\s*[`'"](?:\$\{SITE_URL\}|https:\/\/[^/`'"]+)(\/[^`'"]+)[`'"]/m);
  return match ? match[1] : null;
}

/** `{width, height}` from a PNG IHDR header, or `null` for anything that is not a PNG — the size
 * check is then skipped rather than guessed at, while the existence check above still applies. */
function pngDimensions(bytes) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (bytes.length < 24 || Buffer.compare(bytes.subarray(0, 8), signature) !== 0) {
    return null;
  }
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

/** Problems with the configured social image (empty when clean). Split from the CLI wrapper so the
 * rule is directly testable against fixture config sources and directories. */
export function findSocialImageProblems(configSource, publicDir = SPA_PUBLIC_DIR) {
  const imagePath = parseSocialImagePath(configSource);
  if (imagePath === null) {
    return ['docusaurus.config.ts declares no site-absolute themeConfig.image — every docs page would ship without one'];
  }
  const filePath = join(publicDir, ...imagePath.split('/').filter(Boolean));
  if (!existsSync(filePath)) {
    return [
      `themeConfig.image names ${imagePath}, which no file under ${publicDir} publishes — every docs page's ` +
        'og:image/twitter:image would point at a 404',
    ];
  }
  const dimensions = pngDimensions(readFileSync(filePath));
  if (dimensions === null) {
    return [];
  }
  if (dimensions.width < MIN_LARGE_CARD_WIDTH || dimensions.height < MIN_LARGE_CARD_HEIGHT) {
    return [
      `themeConfig.image is ${dimensions.width}x${dimensions.height}, under the ${MIN_LARGE_CARD_WIDTH}x` +
        `${MIN_LARGE_CARD_HEIGHT} a large summary card needs — Docusaurus always declares ` +
        'twitter:card: summary_large_image, so this renders letterboxed or cropped',
    ];
  }
  return [];
}

if (process.argv[1]?.endsWith('lint-social-image.mjs')) {
  const problems = findSocialImageProblems(readFileSync(CONFIG_PATH, 'utf8'));
  if (problems.length > 0) {
    for (const problem of problems) {
      console.error(`lint-social-image: ${problem}`);
    }
    process.exit(1);
  }
  console.log(`lint-social-image: themeConfig.image ${parseSocialImagePath(readFileSync(CONFIG_PATH, 'utf8'))} is published and large-card sized — clean`);
}
