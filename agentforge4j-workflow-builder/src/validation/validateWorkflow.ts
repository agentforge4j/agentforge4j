import type { ValidationResult, WorkflowDefinition } from '../api/types';

// Phase 5: real schema + cross-reference validation
export function validateWorkflow(_draft: WorkflowDefinition): ValidationResult {
  return {
    valid: true,
    issues: [],
  };
}
