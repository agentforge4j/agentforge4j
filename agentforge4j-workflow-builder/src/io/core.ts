import type { ExportFormat, WorkflowDefinition } from '../api/types';

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

export async function exportBundle(
  workflow: WorkflowDefinition,
  format: ExportFormat,
): Promise<void> {
  if (format === 'zip') {
    const { exportWorkflowZip } = await import('./browser/zip');
    await exportWorkflowZip(workflow);
    return;
  }
  const { downloadWorkflowJson } = await import('./browser/download');
  const name =
    typeof workflow.name === 'string' && workflow.name.length > 0 ? `${workflow.name}.json` : 'workflow.json';
  downloadWorkflowJson(workflow, name);
}

export async function importBundle(file: File): Promise<WorkflowDefinition> {
  if (file.name.endsWith('.zip')) {
    const { importWorkflowZip } = await import('./browser/zip');
    return importWorkflowZip(file);
  }
  const text = await file.text();
  return parseWorkflowJson(text);
}
