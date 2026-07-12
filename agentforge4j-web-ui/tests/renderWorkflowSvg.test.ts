// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { renderWorkflowSvg, type RawExecutable } from '@/lib/renderWorkflowSvg';
import { catalogueData } from '@/lib/catalogueData';

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
});
