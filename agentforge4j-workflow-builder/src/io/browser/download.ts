import type { ExportFormat, WorkflowDefinition } from '../../api/types';
import { parseWorkflowJson, serializeWorkflowJson } from '../core';

// Phase 5: ZIP bundle export
export function downloadWorkflowJson(
  draft: WorkflowDefinition,
  filename = 'workflow.json',
): void {
  const blob = new Blob([serializeWorkflowJson(draft)], {
    type: 'application/json',
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function readWorkflowJsonFile(file: File): Promise<WorkflowDefinition> {
  return file.text().then(parseWorkflowJson);
}

export async function importWorkflowFromFilePicker(): Promise<WorkflowDefinition> {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json';
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) {
        reject(new Error('No file selected'));
        return;
      }
      readWorkflowJsonFile(file).then(resolve).catch(reject);
    };
    input.click();
  });
}

export async function exportWorkflowBundle(
  draft: WorkflowDefinition,
  format: ExportFormat,
): Promise<void> {
  if (format !== 'json') {
    throw new Error(`Unsupported export format: ${format}`);
  }
  const name =
    typeof draft.name === 'string' && draft.name.length > 0
      ? `${draft.name}.json`
      : 'workflow.json';
  downloadWorkflowJson(draft, name);
}
