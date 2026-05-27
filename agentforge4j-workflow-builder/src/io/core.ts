import type { WorkflowDefinition } from '../api/types';

const STRIP_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

export class WorkflowParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WorkflowParseError';
  }
}

/** Rejects prototype-pollution payloads; `constructor` / `prototype` are stripped instead. */
function assertNoProtoKey(value: unknown, path = 'root'): void {
  if (value === null || typeof value !== 'object') {
    return;
  }

  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoProtoKey(item, `${path}[${index}]`));
    return;
  }

  for (const key of Object.keys(value as Record<string, unknown>)) {
    if (key === '__proto__') {
      throw new WorkflowParseError(`Forbidden key "__proto__" at ${path}`);
    }
    assertNoProtoKey((value as Record<string, unknown>)[key], `${path}.${key}`);
  }
}

function stripDangerousKeys(value: unknown): unknown {
  if (value === null || typeof value !== 'object') {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map(stripDangerousKeys);
  }

  const result: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    if (STRIP_KEYS.has(key)) {
      continue;
    }
    result[key] = stripDangerousKeys(child);
  }
  return result;
}

export function serializeWorkflowJson(workflow: WorkflowDefinition): string {
  return JSON.stringify(workflow, null, 2);
}

export function parseWorkflowJson(text: string): WorkflowDefinition {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new WorkflowParseError('Invalid JSON');
  }

  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new WorkflowParseError('Workflow JSON must be an object');
  }

  assertNoProtoKey(parsed);
  return stripDangerousKeys(parsed) as WorkflowDefinition;
}
