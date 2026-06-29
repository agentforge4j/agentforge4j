import { describe, expect, it } from 'vitest';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';

/**
 * Locks the insert-mode banner copy against accidental removal. The banner in
 * WorkflowBuilder renders these keys directly; a missing key would render
 * `undefined` (and fail strict typecheck). Pins the class of bug flagged in
 * review for `chooseStepDescription` / `okShort`.
 */
describe('insert-mode banner copy', () => {
  it.each(['insertStepHere', 'chooseStepDescription', 'okShort'] as const)(
    'ACTION_LABELS.%s is a non-empty string',
    (key) => {
      expect(typeof ACTION_LABELS[key]).toBe('string');
      expect((ACTION_LABELS[key] as string).trim().length).toBeGreaterThan(0);
    },
  );
});
