// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { renderWorkflowSvg, buildWorkflowGraph, type RawExecutable } from '@/lib/renderWorkflowSvg';
import { catalogueData } from '@/lib/catalogueData';
import type dagre from 'dagre';

interface GraphMeta {
  title: string;
}

function idByTitle(g: dagre.graphlib.Graph, title: string): string {
  const match = g.nodes().find((nodeId) => (g.node(nodeId) as unknown as GraphMeta).title === title);
  if (!match) {
    throw new Error(`no node with title '${title}' in graph; nodes: ${g.nodes().join(', ')}`);
  }
  return match;
}

function hasEdge(g: dagre.graphlib.Graph, fromTitle: string, toTitle: string): boolean {
  const from = idByTitle(g, fromTitle);
  const to = idByTitle(g, toTitle);
  return g.edges().some((edge) => edge.v === from && edge.w === to);
}

describe('renderWorkflowSvg', () => {
  test('is deterministic: the same steps in always produce the same markup out', () => {
    const [estimator] = catalogueData.workflows;
    const first = renderWorkflowSvg(estimator.steps);
    const second = renderWorkflowSvg(estimator.steps);
    expect(first).toEqual(second);
  });

  test('emits one root <svg> element', () => {
    const [estimator] = catalogueData.workflows;
    const svg = renderWorkflowSvg(estimator.steps);
    expect(svg.startsWith('<svg')).toBe(true);
    expect(svg.trim().endsWith('</svg>')).toBe(true);
  });

  test('a plain STEP renders the step variant, a BRANCH renders the decision variant', () => {
    const steps: RawExecutable[] = [
      {
        kind: 'STEP',
        stepId: 'route',
        name: 'Route',
        behaviour: {
          type: 'BRANCH',
          contextKey: 'flag',
          branches: {
            yes: { kind: 'STEP', stepId: 'yes-step', name: 'Yes Step', behaviour: { type: 'FAIL', reason: 'x' } },
          },
          defaultBranch: {
            kind: 'STEP',
            stepId: 'default-step',
            name: 'Default Step',
            behaviour: { type: 'FAIL', reason: 'x' },
          },
        },
      },
    ];
    const svg = renderWorkflowSvg(steps);
    expect(svg).toContain('wf-svg-node--decision');
    expect(svg).toContain('wf-svg-node--step');
    // Two branch edges out of the decision node, labeled "yes" and "default".
    expect(svg).toContain('>yes<');
    expect(svg).toContain('>default<');
  });

  test('a BLUEPRINT_REF renders the loop variant', () => {
    const steps: RawExecutable[] = [{ kind: 'BLUEPRINT_REF', blueprintId: 'repeat-body' }];
    const svg = renderWorkflowSvg(steps);
    expect(svg).toContain('wf-svg-node--loop');
    expect(svg).toContain('repeat-body');
  });

  test('escapes XML-significant characters in step names', () => {
    const steps: RawExecutable[] = [
      {
        kind: 'STEP',
        stepId: 'weird-name',
        name: 'Retry & <Fallback> "Step"',
        behaviour: { type: 'FAIL', reason: 'x' },
      },
    ];
    const svg = renderWorkflowSvg(steps);
    expect(svg).not.toContain('<Fallback>');
    expect(svg).toContain('&amp;');
    expect(svg).toContain('&lt;Fallback&gt;');
  });

  test('an empty steps array renders a minimal, valid SVG rather than throwing', () => {
    const svg = renderWorkflowSvg([]);
    expect(svg.startsWith('<svg')).toBe(true);
  });

  test('the root <svg> carries the responsive wf-svg class so it scales into its container', () => {
    const [estimator] = catalogueData.workflows;
    const svg = renderWorkflowSvg(estimator.steps);
    expect(svg.startsWith('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0')).toBe(true);
    expect(svg).toMatch(/^<svg[^>]*\bclass="wf-svg"/);
  });
});

describe('buildWorkflowGraph — branch continuation edges', () => {
  const steps: RawExecutable[] = [
    {
      kind: 'STEP',
      stepId: 'route',
      name: 'Route',
      behaviour: {
        type: 'BRANCH',
        contextKey: 'flag',
        branches: {
          only: { kind: 'STEP', stepId: 'branch-target', name: 'Branch Target', behaviour: { type: 'FAIL', reason: 'x' } },
        },
      },
    },
    { kind: 'STEP', stepId: 'after', name: 'After Branch', behaviour: { type: 'FAIL', reason: 'x' } },
  ];

  test('BRANCH -> branch target -> following top-level step: continuation comes from the branch exit, not the branch node', () => {
    const g = buildWorkflowGraph(steps);
    expect(hasEdge(g, 'Route', 'Branch Target')).toBe(true);
    expect(hasEdge(g, 'Branch Target', 'After Branch')).toBe(true);
  });

  test('no direct edge exists from the branch node to the following top-level step', () => {
    const g = buildWorkflowGraph(steps);
    expect(hasEdge(g, 'Route', 'After Branch')).toBe(false);
  });

  test('a BRANCH with no renderable target falls back to itself as the exit, so a following step is not silently dropped', () => {
    const emptyBranchSteps: RawExecutable[] = [
      { kind: 'STEP', stepId: 'route2', name: 'Empty Route', behaviour: { type: 'BRANCH', contextKey: 'flag', branches: {} } },
      { kind: 'STEP', stepId: 'after2', name: 'After Empty Branch', behaviour: { type: 'FAIL', reason: 'x' } },
    ];
    const g = buildWorkflowGraph(emptyBranchSteps);
    expect(hasEdge(g, 'Empty Route', 'After Empty Branch')).toBe(true);
  });
});
