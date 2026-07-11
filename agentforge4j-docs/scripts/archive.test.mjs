// SPDX-License-Identifier: Apache-2.0
//
// Tests for the pure halves of the archive transition (design §7/§12): route derivation from an
// export's file listing, and the old-address -> archive-address redirect manifest. The full
// mechanism (isolated export, frozen artifact, versions.json removal, net-zero revert) is proven
// end-to-end by `npm run docs:archive-scratch`.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {pageRoutes, redirectManifest} from './archive-transition.mjs';

test('pageRoutes: every index.html directory is a route; assets and 404 are not', () => {
  const files = [
    '404.html',
    'assets/css/styles.css',
    'assets/js/main.js',
    'get-started/evaluating/index.html',
    'index.html',
    'reference/config/index.html',
    'sitemap.xml',
  ];
  assert.deepEqual(pageRoutes(files), ['', 'get-started/evaluating', 'reference/config']);
});

test('pageRoutes: empty export has no routes', () => {
  assert.deepEqual(pageRoutes(['404.html', 'assets/css/styles.css']), []);
});

test('redirectManifest: maps each old active address to the same route under the archive mount', () => {
  assert.deepEqual(redirectManifest('1.0.0', ['', 'reference/config']), [
    {from: '/docs/1.0.0', to: '/docs/archive/1.0.0'},
    {from: '/docs/1.0.0/reference/config', to: '/docs/archive/1.0.0/reference/config'},
  ]);
});
