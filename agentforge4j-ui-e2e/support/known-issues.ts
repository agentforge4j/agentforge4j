// SPDX-License-Identifier: Apache-2.0

import { test as base, type PlaywrightTestArgs, type PlaywrightTestOptions, type TestInfo } from '@playwright/test';

/** `test.fixme`/`test()`'s own body-function type is defined via an overloaded signature that
 *  `Parameters<typeof base.fixme>` cannot extract correctly (TS only resolves the last overload) —
 *  spelled out explicitly here instead. */
type BuilderTestFn = (
  args: PlaywrightTestArgs & PlaywrightTestOptions,
  testInfo: TestInfo,
) => Promise<void> | void;

/**
 * Generic regression/quarantine mechanism for Builder functional tests — not specific to any one
 * batch of issues. Two states:
 *
 * - **Mandatory regression test**: written as a plain `test(...)` directly in an `f*.spec.ts`
 *   file. Runs in every project (`desktop`/`tablet`/`mobile`) and gates normal CI.
 * - **Quarantined test**: written via {@link knownIssueTest} instead of plain `test(...)`. This
 *   tags the title `@known-issue #<n>`, which `playwright.builder-functional.config.ts`'s
 *   `desktop`/`tablet`/`mobile` projects all `grepInvert` — so it is entirely absent from normal
 *   CI (not merely skipped-and-visible; excluded up front), while it is a **plain, fully-executing
 *   `test()`** underneath — deliberately *not* `test.fixme()`, whose body never actually runs even
 *   under `--grep`, which would defeat the point of a *strict* command. `npm run
 *   test:e2e:builder-functional:known-issues` (`--grep @known-issue`, no project's `grepInvert`
 *   applies to a direct `--grep` invocation) runs it for real and reports its genuine pass/fail —
 *   the point of exercising it during remediation work.
 * - **Promotion path**: once the fix merges, move the test out of `knownIssueTest(...)` into a
 *   plain `test(...)` call (dropping the `@known-issue` tag) and flip the entry's `status` to
 *   `'closed'` here, for record-keeping. It now runs in every project like any other test.
 *
 * #94-#103 (the builder-usability-remediation-plan issues this testkit was originally built
 * against) are all listed below as worked examples, and all are `'closed'` — every remediation PR
 * (#105-110) had already merged by the time this testkit was built, so none of them are actually
 * quarantined; they run as ordinary mandatory tests via `test()` directly in the `f*.spec.ts`
 * files, not through `knownIssueTest`. This registry and {@link knownIssueTest} exist for the
 * *next* Builder defect found during future work, not for #94-#103 specifically.
 */
export type KnownIssue = {
  title: string;
  status: 'open' | 'closed';
  /** PR that closed the issue, when status is 'closed'. */
  closedBy?: string;
};

export const KNOWN_ISSUES: Record<number, KnownIssue> = {
  94: { title: 'Reloading the page silently discards all in-progress work', status: 'closed', closedBy: '#105' },
  95: { title: 'Validation summary badge gives no detail', status: 'closed', closedBy: '#106' },
  96: { title: 'No undo/redo for any meaningful change', status: 'closed', closedBy: '#107' },
  97: { title: "Mobile '+ Add step' does not open the step palette", status: 'closed', closedBy: '#110' },
  98: { title: 'Mobile workflow-name field obstructed by mode-toggle', status: 'closed', closedBy: '#110' },
  99: { title: '3 of 6 step types clipped and unreachable', status: 'closed', closedBy: '#108' },
  100: { title: "No explicit 'start step' indicator/selector in Guided mode", status: 'closed', closedBy: '#109' },
  101: { title: "Guided checklist items don't match actual validation", status: 'closed', closedBy: '#106' },
  102: { title: 'No confirmation/feedback shown after Export completes', status: 'closed', closedBy: '#109' },
  103: { title: 'Mobile guided-checklist overlay blocks the canvas', status: 'closed', closedBy: '#110' },
};

/**
 * Registers a quarantined test for a scenario expected to fail until a specific issue is fixed.
 * Prefer plain `test()` for anything that already passes against current `main` — quarantining a
 * passing test would only hide it from normal CI for no reason.
 *
 * @param issueNumber - key into {@link KNOWN_ISSUES}, purely for the title tag / traceability;
 *   does not have to already exist in the registry (add it there too, for the record).
 * @param title - the test title, without the `@known-issue` tag (added automatically).
 */
export function knownIssueTest(issueNumber: number, title: string, fn: BuilderTestFn): void {
  base(`${title} @known-issue #${issueNumber}`, fn);
}
