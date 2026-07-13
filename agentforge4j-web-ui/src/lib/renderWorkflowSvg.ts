// SPDX-License-Identifier: Apache-2.0
//
// Deterministic, non-interactive SVG renderer for a shipped workflow's step graph (catalogue
// detail page, design §7's static-fallback rendering path — no visualizer/builder embed exists
// for 0.1.0). Layout is computed by `dagre`, the same headless graph-layout library the
// interactive builder canvas already depends on (agentforge4j-workflow-builder/package.json) —
// reused here for layout only, never for rendering: `dagre` has no DOM/React dependency and
// produces plain node/edge coordinates, unlike `@xyflow/react` (React Flow), which is
// interactive/DOM-only and was explicitly ruled out for this use (catalogue-track implementation
// plan §4). The SVG markup itself is hand-rolled — dagre has no rendering opinion.
//
// Node "kind" is reduced to the same three visual variants the interactive canvas uses
// (agentforge4j-workflow-builder/src/canvas/nodes/{Step,Decision,Loop}Node.tsx, via
// NodeChrome's `variant` prop): a BRANCH step reads as a decision, a BLUEPRINT_REF (the on-disk
// representation of the builder's REPEAT/loop construct — see that module's
// `model/mapper.ts#buildBlueprintJsonForRepeat`) reads as a loop, everything else reads as a
// plain step.

import dagre from 'dagre';

/**
 * One entry of a workflow's `steps` array, or a nested branch/predicate/fallback target. Matches
 * the workflow schema's `Executable` union
 * (agentforge4j-schema/src/main/resources/schema/workflow.schema.json): a `STEP`, a
 * `BLUEPRINT_REF`, or — schema-legal but never seen in a real shipped workflow — a fully nested
 * `WorkflowDefinition`. This renderer treats that third case as an opaque leaf (does not recurse
 * into its own `steps`) since no real document exercises that shape; extending it is future work,
 * not a gap papered over here.
 */
export type RawExecutable = Record<string, unknown>;

interface GraphNodeMeta {
  variant: 'step' | 'decision' | 'loop';
  title: string;
  subtitle: string;
}

interface DagreNode extends GraphNodeMeta {
  x: number;
  y: number;
}

interface DagreEdge {
  points: Array<{ x: number; y: number }>;
  label?: string;
}

const NODE_WIDTH = 200;
const NODE_HEIGHT = 64;
const RANK_SEP = 56;
const NODE_SEP = 32;
const MARGIN = 16;
const TITLE_MAX_CHARS = 28;
const SUBTITLE_MAX_CHARS = 32;

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max - 1)}…` : value;
}

function isExecutable(value: unknown): value is RawExecutable {
  return typeof value === 'object' && value !== null;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function classify(node: RawExecutable): GraphNodeMeta {
  const kind = asString(node.kind);
  if (kind === 'BLUEPRINT_REF') {
    const blueprintId = asString(node.blueprintId) ?? 'Loop';
    return { variant: 'loop', title: blueprintId, subtitle: 'Loop' };
  }
  const behaviour = isExecutable(node.behaviour) ? node.behaviour : undefined;
  const behaviourType = asString(behaviour?.type);
  const title = asString(node.name) ?? asString(node.stepId) ?? kind ?? 'Step';
  if (behaviourType === 'BRANCH') {
    return { variant: 'decision', title, subtitle: asString(behaviour?.contextKey) ?? 'Branch' };
  }
  return { variant: 'step', title, subtitle: behaviourType ?? kind ?? '' };
}

let idCounter = 0;

function nextId(prefix: string): string {
  idCounter += 1;
  return `${prefix.replace(/[^a-zA-Z0-9_-]/g, '_')}-${idCounter}`;
}

/**
 * Walks one Executable, registering it (and everything it points to) as dagre nodes/edges.
 * Returns the dagre node id assigned to `node`, so the caller can chain a sequential edge into it.
 */
function walk(g: dagre.graphlib.Graph, node: RawExecutable, parentId: string | null, edgeLabel: string | null): string {
  const meta = classify(node);
  const idPrefix = asString(node.stepId) ?? asString(node.blueprintId) ?? 'step';
  const id = nextId(idPrefix);
  g.setNode(id, { width: NODE_WIDTH, height: NODE_HEIGHT, ...meta });
  if (parentId) {
    g.setEdge(parentId, id, { label: edgeLabel ?? '' });
  }

  const behaviour = isExecutable(node.behaviour) ? node.behaviour : undefined;
  const behaviourType = asString(behaviour?.type);
  if (behaviourType === 'BRANCH' && behaviour) {
    const branches = isExecutable(behaviour.branches) ? behaviour.branches : {};
    for (const [key, target] of Object.entries(branches)) {
      if (isExecutable(target)) {
        walk(g, target, id, key);
      }
    }
    const predicates = Array.isArray(behaviour.predicates) ? behaviour.predicates : [];
    predicates.forEach((predicate, index) => {
      if (isExecutable(predicate) && isExecutable(predicate.target)) {
        walk(g, predicate.target, id, asString(predicate.kind) ?? `predicate-${index}`);
      }
    });
    if (isExecutable(behaviour.defaultBranch)) {
      walk(g, behaviour.defaultBranch, id, 'default');
    }
  }
  if (behaviourType === 'RETRY_PREVIOUS' && behaviour && isExecutable(behaviour.fallback)) {
    walk(g, behaviour.fallback, id, 'fallback');
  }

  return id;
}

function nodeMarkup(x: number, y: number, meta: GraphNodeMeta): string {
  const left = x - NODE_WIDTH / 2;
  const top = y - NODE_HEIGHT / 2;
  const title = escapeXml(truncate(meta.title, TITLE_MAX_CHARS));
  const subtitle = escapeXml(truncate(meta.subtitle, SUBTITLE_MAX_CHARS));

  let shape: string;
  if (meta.variant === 'decision') {
    const points = `${x},${top} ${left + NODE_WIDTH},${y} ${x},${top + NODE_HEIGHT} ${left},${y}`;
    shape = `<polygon points="${points}" class="wf-svg-node wf-svg-node--decision" />`;
  } else if (meta.variant === 'loop') {
    shape = `<rect x="${left}" y="${top}" width="${NODE_WIDTH}" height="${NODE_HEIGHT}" rx="${NODE_HEIGHT / 2}" class="wf-svg-node wf-svg-node--loop" />`;
  } else {
    shape = `<rect x="${left}" y="${top}" width="${NODE_WIDTH}" height="${NODE_HEIGHT}" rx="8" class="wf-svg-node wf-svg-node--step" />`;
  }

  const subtitleMarkup = subtitle
    ? `<text x="${x}" y="${y + 14}" text-anchor="middle" class="wf-svg-node-subtitle">${subtitle}</text>`
    : '';

  return `${shape}<text x="${x}" y="${y - 6}" text-anchor="middle" class="wf-svg-node-title">${title}</text>${subtitleMarkup}`;
}

function edgeMarkup(points: Array<{ x: number; y: number }>, label: string): string {
  if (points.length === 0) {
    return '';
  }
  const [first, ...rest] = points;
  const path = `M ${first.x},${first.y} ${rest.map((p) => `L ${p.x},${p.y}`).join(' ')}`;
  const mid = points[Math.floor(points.length / 2)];
  const labelMarkup = label
    ? `<text x="${mid.x + 6}" y="${mid.y - 4}" class="wf-svg-edge-label">${escapeXml(label)}</text>`
    : '';
  return `<path d="${path}" class="wf-svg-edge" marker-end="url(#wf-svg-arrow)" />${labelMarkup}`;
}

/**
 * Renders a shipped workflow's `steps` array (the raw, schema-validated JSON as carried through
 * unmodified by build-catalogue-data.mjs) to a self-contained, deterministic SVG markup string.
 * Pure function of its input: the same steps in always produce the same markup out — no
 * randomness, no DOM access, no interactivity.
 */
export function renderWorkflowSvg(steps: readonly RawExecutable[]): string {
  idCounter = 0;
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: 'TB', ranksep: RANK_SEP, nodesep: NODE_SEP, marginx: MARGIN, marginy: MARGIN });
  g.setDefaultEdgeLabel(() => ({}));

  let previousTopLevelId: string | null = null;
  for (const step of steps) {
    if (!isExecutable(step)) {
      continue;
    }
    const id = walk(g, step, null, null);
    if (previousTopLevelId) {
      g.setEdge(previousTopLevelId, id, { label: '' });
    }
    previousTopLevelId = id;
  }

  if (g.nodeCount() === 0) {
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1" width="1" height="1" role="presentation" />';
  }

  dagre.layout(g);

  const graphMeta = g.graph();
  const width = Math.max(1, Math.ceil(graphMeta.width ?? 0));
  const height = Math.max(1, Math.ceil(graphMeta.height ?? 0));

  const nodeMarkups = g
    .nodes()
    .map((nodeId) => {
      const n = g.node(nodeId) as unknown as DagreNode;
      return nodeMarkup(n.x, n.y, n);
    })
    .join('');

  const edgeMarkups = g
    .edges()
    .map((edgeId) => {
      const edge = g.edge(edgeId) as unknown as DagreEdge;
      return edgeMarkup(edge.points, edge.label ?? '');
    })
    .join('');

  return [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" class="wf-svg">`,
    '<defs>',
    '<marker id="wf-svg-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">',
    '<path d="M 0 0 L 10 5 L 0 10 z" class="wf-svg-arrowhead" />',
    '</marker>',
    '</defs>',
    `<g class="wf-svg-edges">${edgeMarkups}</g>`,
    `<g class="wf-svg-nodes">${nodeMarkups}</g>`,
    '</svg>',
  ].join('');
}
