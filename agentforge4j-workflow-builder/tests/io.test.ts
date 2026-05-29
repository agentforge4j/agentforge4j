import { describe, expect, it } from 'vitest';
import {
  parseWorkflowJson,
  serializeWorkflowJson,
  WorkflowParseError,
} from '../src/io/core';
import type { WorkflowDefinition } from '../src/api/types';

describe('workflow JSON io', () => {
  const sample: WorkflowDefinition = {
    id: 'wf-1',
    name: 'Sample',
    description: 'Demo',
    steps: [],
    artifacts: {},
  };

  it('round-trips through serialize and parse', () => {
    const text = serializeWorkflowJson(sample);
    const parsed = parseWorkflowJson(text);
    expect(parsed).toEqual(sample);
  });

  it('rejects __proto__-poisoned payloads', () => {
    const poisoned = '{"__proto__":{"polluted":true},"name":"x"}';
    expect(() => parseWorkflowJson(poisoned)).toThrow(WorkflowParseError);
  });

  it('strips nested dangerous keys when absent at root assertion path', () => {
    const nested = JSON.stringify({
      name: 'safe',
      nested: { prototype: { bad: true }, ok: 1 },
    });
    const parsed = parseWorkflowJson(nested);
    expect(parsed).toEqual({ name: 'safe', nested: { ok: 1 } });
  });
});
