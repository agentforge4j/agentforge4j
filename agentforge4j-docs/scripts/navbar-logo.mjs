// SPDX-License-Identifier: Apache-2.0
//
// Pure parsing helper for the navbar logo gate. Extracts the `navbar.logo` object's
// `src`/`srcDark` string values straight from the `docusaurus.config.ts` source text — the
// config is plain data (`satisfies Preset.ThemeConfig`), so a text-level parse avoids the
// cost/complexity of transpiling and executing TypeScript just to read two literals.

/** Returns the substring of `source` for the first balanced `{...}` block starting at
 *  the first `{` at or after `fromIndex`, or null if none closes. */
function extractBalancedBlock(source, fromIndex) {
  const start = source.indexOf('{', fromIndex);
  if (start === -1) {
    return null;
  }
  let depth = 0;
  for (let i = start; i < source.length; i += 1) {
    if (source[i] === '{') depth += 1;
    else if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(start, i + 1);
      }
    }
  }
  return null;
}

/** Extracts `{src, srcDark}` from the `themeConfig.navbar.logo` object in a
 *  `docusaurus.config.ts` source string. Either field is null if absent. */
export function parseNavbarLogo(configSource) {
  const navbarIndex = configSource.indexOf('navbar:');
  if (navbarIndex === -1) {
    throw new Error('parseNavbarLogo: no `navbar:` key found in config source');
  }
  const navbarBlock = extractBalancedBlock(configSource, navbarIndex);
  if (navbarBlock === null) {
    throw new Error('parseNavbarLogo: `navbar:` object is not balanced/closed');
  }

  const logoIndex = navbarBlock.indexOf('logo:');
  if (logoIndex === -1) {
    return {src: null, srcDark: null};
  }
  const logoBlock = extractBalancedBlock(navbarBlock, logoIndex);
  if (logoBlock === null) {
    throw new Error('parseNavbarLogo: `navbar.logo:` object is not balanced/closed');
  }

  const srcMatch = logoBlock.match(/\bsrc:\s*['"]([^'"]+)['"]/);
  const srcDarkMatch = logoBlock.match(/srcDark:\s*['"]([^'"]+)['"]/);
  return {
    src: srcMatch ? srcMatch[1] : null,
    srcDark: srcDarkMatch ? srcDarkMatch[1] : null,
  };
}
