import type { ExportFormat, ExportOutcome, WorkflowDefinition } from '../api/types';

const STRIP_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

export class WorkflowParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WorkflowParseError';
  }
}

export function sanitizeObject<T>(value: T): T {
  if (value === null || typeof value !== 'object') {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map((item) => sanitizeObject(item)) as T;
  }

  const result: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    if (STRIP_KEYS.has(key)) {
      continue;
    }
    result[key] = sanitizeObject(child);
  }
  return result as T;
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
  return sanitizeObject(parsed as WorkflowDefinition);
}

/**
 * Export the workflow as a browser download. Delegates to {@link exportWorkflowBundle} (the
 * builder's built-in default adapter) so both public export entry points share one
 * implementation and both resolve the same {@link ExportOutcome} naming the produced file —
 * a host wiring this helper as its `exportBundle` adapter gets the exact-filename
 * confirmation, not the generic one.
 */
export async function exportBundle(
  workflow: WorkflowDefinition,
  format: ExportFormat,
): Promise<ExportOutcome> {
  const { exportWorkflowBundle } = await import('./browser/download');
  return exportWorkflowBundle(workflow, format);
}

export async function importBundle(file: File): Promise<WorkflowDefinition> {
  if (file.name.endsWith('.zip')) {
    const { importWorkflowZip } = await import('./browser/zip');
    return importWorkflowZip(file);
  }
  const text = await file.text();
  return parseWorkflowJson(text);
}
