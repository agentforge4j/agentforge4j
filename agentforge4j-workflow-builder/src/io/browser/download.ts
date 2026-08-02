// SPDX-License-Identifier: Apache-2.0

import type { ExportFormat, ExportOutcome, WorkflowDefinition } from '../../api/types';
import { serializeWorkflowJson } from '../core';

export function downloadWorkflowJson(draft: WorkflowDefinition, filename = 'workflow.json'): void {
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

export async function exportWorkflowBundle(
  draft: WorkflowDefinition,
  format: ExportFormat,
): Promise<ExportOutcome> {
  if (format === 'zip') {
    const { exportWorkflowZip, workflowZipFileName } = await import('./zip');
    await exportWorkflowZip(draft);
    // Reported from the same helper exportWorkflowZip downloads under, so the confirmation
    // names the exact produced file by construction.
    return { filename: workflowZipFileName(draft) };
  }
  const name =
    typeof draft.name === 'string' && draft.name.length > 0 ? `${draft.name}.json` : 'workflow.json';
  downloadWorkflowJson(draft, name);
  return { filename: name };
}
