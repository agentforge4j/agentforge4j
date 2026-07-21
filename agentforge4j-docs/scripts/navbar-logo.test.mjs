// SPDX-License-Identifier: Apache-2.0
//
// Tests for the navbar-logo config parser powering the srcDark regression gate.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseNavbarLogo} from './navbar-logo.mjs';

function wrap(logoBody) {
  return `
    themeConfig: {
      navbar: {
        title: 'AgentForge4j',
        logo: {
          alt: 'AgentForge4j',
          ${logoBody}
        },
        items: [{label: 'GitHub', href: 'x'}],
      },
    },
  `;
}

test('extracts both src and srcDark when present', () => {
  const {src, srcDark} = parseNavbarLogo(
    wrap(`src: 'img/logo-horizontal.svg',\n          srcDark: 'img/logo-horizontal-dark.svg',`),
  );
  assert.equal(src, 'img/logo-horizontal.svg');
  assert.equal(srcDark, 'img/logo-horizontal-dark.svg');
});

test('reports srcDark as null when the field is absent (the regression this gate catches)', () => {
  const {src, srcDark} = parseNavbarLogo(wrap(`src: 'img/logo-horizontal.svg',`));
  assert.equal(src, 'img/logo-horizontal.svg');
  assert.equal(srcDark, null);
});

test('is not confused by unrelated src-like keys elsewhere in the config', () => {
  const source = `
    someOtherPlugin: {
      src: 'not-the-navbar-logo.svg',
    },
    ${wrap(`src: 'img/logo-horizontal.svg',\n          srcDark: 'img/logo-horizontal-dark.svg',`)}
  `;
  const {src, srcDark} = parseNavbarLogo(source);
  assert.equal(src, 'img/logo-horizontal.svg');
  assert.equal(srcDark, 'img/logo-horizontal-dark.svg');
});

test('throws a clear error when navbar: is missing entirely', () => {
  assert.throws(() => parseNavbarLogo('themeConfig: { footer: {} }'), /navbar:/);
});
