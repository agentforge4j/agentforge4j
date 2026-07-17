// SPDX-License-Identifier: Apache-2.0

/**
 * Shared viewport matrix for the Builder functional testkit
 * (`specs/builder-functional/**`). Kept separate from `support/web-ui/routes.ts`'s
 * `VIEWPORTS` (visual-review capture matrix, different purpose/values) — this one is sized for
 * interaction testing, not screenshot comparison.
 */
export const FUNCTIONAL_VIEWPORTS = {
  /** Below the builder's 767px narrow-container breakpoint; smallest common phone width. */
  mobileNarrow: { width: 360, height: 740 },
  /** Below the breakpoint; iPhone 12/13-class width — the width issues #97/#98/#103 were found at. */
  mobileStandard: { width: 390, height: 844 },
  /** Above the breakpoint; portrait tablet. */
  tablet: { width: 768, height: 1024 },
  /** Above the breakpoint; standard laptop — the primary desktop target. */
  desktop: { width: 1440, height: 900 },
  /** Above the breakpoint; spot-checked only where a layout could regress at extra width. */
  largeDesktop: { width: 1920, height: 1080 },
} as const;
