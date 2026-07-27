// SPDX-License-Identifier: Apache-2.0
//
// Navbar logo dark-mode gate. `colorMode.respectPrefersColorScheme` is on, so Docusaurus
// switches to `navbar.logo.srcDark` automatically in dark colour mode — if that field is
// missing, the light (dark-text) mark is served on the dark navbar and becomes unreadable.
// This asserts `srcDark` is set, distinct from `src`, and that both referenced static
// files actually exist.

import {existsSync, readFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseNavbarLogo} from './navbar-logo.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const CONFIG_PATH = join(MODULE_ROOT, 'docusaurus.config.ts');

const {src, srcDark} = parseNavbarLogo(readFileSync(CONFIG_PATH, 'utf8'));
const errors = [];

if (!src) {
  errors.push('navbar.logo.src is missing.');
}
if (!srcDark) {
  errors.push(
    'navbar.logo.srcDark is missing — the light logo will be served on the dark navbar and become unreadable.',
  );
}
if (src && srcDark && src === srcDark) {
  errors.push(`navbar.logo.src and srcDark point at the same asset ('${src}') — no real dark variant is wired.`);
}
for (const [field, value] of [['src', src], ['srcDark', srcDark]]) {
  if (value && !existsSync(join(MODULE_ROOT, 'static', value))) {
    errors.push(`navbar.logo.${field} ('${value}') does not resolve to a file under static/.`);
  }
}

if (errors.length > 0) {
  for (const message of errors) {
    console.error(`✗ ${message}`);
  }
  process.exit(1);
}

console.log(`navbar-logo: light '${src}' and dark '${srcDark}' variants both present and wired.`);
