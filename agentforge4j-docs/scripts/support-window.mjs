// SPDX-License-Identifier: Apache-2.0
//
// Supported-version window (design §3/§7, Phase 5a).
//
// A pure function over Docusaurus's `versions.json` (an array of released version strings, newest
// first) and an LTS list (`lts.json`). It classifies each released version as supported or archived
// per the policy: the newest stable, the one immediately prior, and any designated LTS are supported;
// everything older is archived. `latest` is the newest stable (the target the `/latest` alias points
// at). Pre-`0.1.0` the released list is empty, so `latest` is null and nothing is archived.
//
// This drives the redirect toggle (see redirect-config.mjs) and, later, archive banners/removal. It is
// deliberately I/O-free: callers read the two JSON inputs and pass the parsed arrays.

/**
 * Classify released versions into the supported window vs the archive.
 *
 * @param {string[]} versions Docusaurus `versions.json` contents (released versions, newest first)
 * @param {string[]} [lts] versions designated long-term-supported (`lts.json`)
 * @returns {{latest: string|null, supported: string[], archived: string[]}}
 */
export function supportWindow(versions, lts = []) {
  if (!Array.isArray(versions)) {
    throw new Error('supportWindow: `versions` must be an array');
  }
  if (!Array.isArray(lts)) {
    throw new Error('supportWindow: `lts` must be an array');
  }
  if (versions.length === 0) {
    return {latest: null, supported: [], archived: []};
  }

  const ltsSet = new Set(lts);
  const supportedSet = new Set();
  // Newest stable + the one immediately prior.
  supportedSet.add(versions[0]);
  if (versions.length > 1) {
    supportedSet.add(versions[1]);
  }
  // Any LTS version that is actually a released version.
  for (const version of versions) {
    if (ltsSet.has(version)) {
      supportedSet.add(version);
    }
  }

  // Preserve the newest-first order of `versions` in both output lists.
  const supported = versions.filter((version) => supportedSet.has(version));
  const archived = versions.filter((version) => !supportedSet.has(version));
  return {latest: versions[0], supported, archived};
}
