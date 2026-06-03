// SPDX-License-Identifier: Apache-2.0

import type { ExportFormat, WorkflowDefinition } from '../../api/types';
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
): Promise<void> {
  if (format === 'zip') {
    const { exportWorkflowZip } = await import('./zip');
    await exportWorkflowZip(draft);
    return;
  }
  const name =
    typeof draft.name === 'string' && draft.name.length > 0 ? `${draft.name}.json` : 'workflow.json';
  downloadWorkflowJson(draft, name);
}
