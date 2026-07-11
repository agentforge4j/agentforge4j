// SPDX-License-Identifier: Apache-2.0
//
// JavadocLink remark role. Authors write an inline-code tag
// `javadoc:com.agentforge4j.<fqcn>` and this resolves it, at build time, to a version-pinned link
// into the stitched Javadoc surface, rendering the simple class name as a clickable code link.
//
//   `javadoc:com.agentforge4j.core.workflow.Workflow`
//     -> /javadoc/next/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html
//   `javadoc:com.agentforge4j.mcp.client.McpServerRegistry`
//     -> /javadoc/next/mcp/com/agentforge4j/mcp/client/McpServerRegistry.html
//   `javadoc:com.agentforge4j.starter.AgentForge4jProperties`
//     -> /javadoc/next/spring-boot-starter/com/agentforge4j/starter/AgentForge4jProperties.html
//
// URLs use the `pathname://` prefix so Docusaurus emits a plain link to the sibling /javadoc artifact
// (outside the /docs build) without route/broken-link checking and without hardcoding a host. The role
// is narrow: it throws on a non-AgentForge4j FQCN or an unresolvable module, so a bad reference fails
// the build rather than producing a dead link.

// The current (editable) docs version. Live builds resolve against `next`; the release-staging
// de-materialiser passes the snapshot's own version so a frozen page links into its
// own Javadoc surface (`/javadoc/<version>/…`) instead of the moving `next`.
export const DEFAULT_VERSION = 'next';

// Surfaces. The aggregate documents named modules under a module directory; mcp/starter are flat
// (classpath-mode) surfaces. The module set MUST match the agentforge4j-docs-javadoc aggregator's
// <modules> (scripts/javadoc.test.mjs asserts this so the two cannot drift).
export const AGGREGATE_MODULES = [
  'agentforge4j.util',
  'agentforge4j.llm.api',
  'agentforge4j.llm',
  'agentforge4j.llm.gemini',
  'agentforge4j.llm.fake',
  'agentforge4j.core',
  'agentforge4j.tools.http',
  'agentforge4j.schema',
  'agentforge4j.config.loader',
  'agentforge4j.runtime',
  'agentforge4j.bootstrap',
];
const FLAT_SURFACES = [
  {packagePrefix: 'agentforge4j.mcp', surface: 'mcp'},
  {packagePrefix: 'agentforge4j.starter', surface: 'spring-boot-starter'},
];

/** Split an FQCN into its package path and class file (nested classes keep dots: Outer.Inner.html). */
function splitFqcn(fqcn) {
  const segs = fqcn.split('.');
  const firstClassIdx = segs.findIndex((s) => /^[A-Z]/.test(s));
  if (firstClassIdx <= 0) {
    return null;
  }
  const pkgSegs = segs.slice(0, firstClassIdx);
  const classSegs = segs.slice(firstClassIdx);
  return {
    pkgSegs,
    pkgPath: pkgSegs.join('/'),
    classFile: `${classSegs.join('.')}.html`,
    simpleName: classSegs[classSegs.length - 1],
  };
}

/**
 * Resolve an AgentForge4j FQCN to its /javadoc/<version>/ URL. Throws if it cannot be placed.
 *
 * @param {string} fqcn the fully-qualified com.agentforge4j.* class name
 * @param {string} [version] the Javadoc surface version to pin the link to (default `next`)
 */
export function resolveJavadocUrl(fqcn, version = DEFAULT_VERSION) {
  if (!fqcn.startsWith('com.agentforge4j.')) {
    throw new Error(`javadoc: '${fqcn}' is not a com.agentforge4j.* type`);
  }
  const parts = splitFqcn(fqcn);
  if (parts === null) {
    throw new Error(`javadoc: cannot parse a class name out of '${fqcn}'`);
  }
  const comStrippedPkg = parts.pkgSegs.slice(1).join('.'); // drop leading "com"

  const flat = FLAT_SURFACES.find(
    (s) => comStrippedPkg === s.packagePrefix || comStrippedPkg.startsWith(`${s.packagePrefix}.`),
  );
  if (flat) {
    return {
      url: `pathname:///javadoc/${version}/${flat.surface}/${parts.pkgPath}/${parts.classFile}`,
      simpleName: parts.simpleName,
    };
  }

  // Aggregate (modular): pick the longest module name that prefixes the package, at a dot boundary.
  const module = AGGREGATE_MODULES
    .filter((m) => comStrippedPkg === m || comStrippedPkg.startsWith(`${m}.`))
    .sort((a, b) => b.length - a.length)[0];
  if (!module) {
    throw new Error(`javadoc: no Javadoc surface owns package '${comStrippedPkg}' (from '${fqcn}')`);
  }
  return {
    url: `pathname:///javadoc/${version}/${module}/${parts.pkgPath}/${parts.classFile}`,
    simpleName: parts.simpleName,
  };
}

export const PATTERN = /^javadoc:(com\.agentforge4j\.[\w.$]+)$/;

function walk(node, visit) {
  if (!node || typeof node !== 'object') {
    return;
  }
  if (Array.isArray(node.children)) {
    for (let i = 0; i < node.children.length; i += 1) {
      const child = node.children[i];
      if (child.type === 'inlineCode' && PATTERN.test(child.value)) {
        visit(node, i, child);
      } else {
        walk(child, visit);
      }
    }
  }
}

/**
 * Remark plugin: rewrite `javadoc:<fqcn>` inline code into a link into the Javadoc surface.
 *
 * @param {{version?: string}} [options] optional Javadoc-surface version to pin links to (default `next`)
 */
export default function javadocRemarkPlugin(options = {}) {
  const version = options.version || DEFAULT_VERSION;
  return (tree, file) => {
    walk(tree, (parent, index, node) => {
      const fqcn = PATTERN.exec(node.value)[1];
      let resolved;
      try {
        resolved = resolveJavadocUrl(fqcn, version);
      } catch (err) {
        const where = file && file.path ? ` (${file.path})` : '';
        throw new Error(`${err.message}${where}`);
      }
      parent.children[index] = {
        type: 'link',
        url: resolved.url,
        title: fqcn,
        children: [{type: 'inlineCode', value: resolved.simpleName}],
      };
    });
  };
}
