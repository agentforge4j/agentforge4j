// SPDX-License-Identifier: Apache-2.0
//
// Shared file-extension -> Content-Type map for this module's two static file servers:
// specs/web-ui/hosting.spec.ts (the un-assembled SPA dist/) and
// scripts/visual/serve-assembled-site.mjs (the fully composed site, which also serves Docusaurus
// and Javadoc output — hence more extensions here than hosting.spec.ts alone would ever need).
// Previously two independently hand-maintained maps that had already drifted apart.

export const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.mjs': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain',
  '.xml': 'application/xml',
};
