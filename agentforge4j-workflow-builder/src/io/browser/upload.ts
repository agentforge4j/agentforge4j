// SPDX-License-Identifier: Apache-2.0

import type { WorkflowDefinition } from '../../api/types';
import { importBundle } from '../core';

export function readWorkflowJsonFile(file: File): Promise<WorkflowDefinition> {
  return importBundle(file);
}

export async function importWorkflowFromFilePicker(): Promise<WorkflowDefinition> {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json,application/zip,.zip';
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) {
        reject(new Error('No file selected'));
        return;
      }
      importBundle(file).then(resolve).catch(reject);
    };
    input.click();
  });
}
