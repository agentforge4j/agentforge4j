// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { RELEVANT_PATH_PATTERN, parseDirtyRelevantFiles, parseAttestation } from '../../scripts/visual/check-freshness.mjs';

test.describe('RELEVANT_PATH_PATTERN — attestation freshness change-detection', () => {
  test('an attestation-only commit does NOT register as a relevant UI change — the circular self-invalidation bug this closes', () => {
    // Committing the attestation file is itself a change under agentforge4j-ui-e2e/ — widening the
    // pattern from `agentforge4j-ui-e2e/visual/` to the whole tree (to also catch scripts/specs/
    // support changes) reintroduced the exact bug main()'s own "Deliberately NOT commitSha !==
    // currentSha" comment already describes: an attestation-only commit would predate ITSELF.
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/visual-evidence/attestation.json')).toBe(false);
  });

  test('a genuine manifest change still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/visual/manifest.ts')).toBe(true);
  });

  test('a change to the capture orchestrator still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/specs/visual/capture.spec.ts')).toBe(true);
  });

  test('a change to the local static server still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/scripts/visual/serve-assembled-site.mjs')).toBe(true);
  });

  test('a change to the manifest\'s own route data source still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-ui-e2e/support/web-ui/routes.ts')).toBe(true);
  });

  test('a Node version bump (.nvmrc) still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('.nvmrc')).toBe(true);
  });

  test('a change to the ui-e2e CI workflow itself still registers as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('.github/workflows/ui-e2e.yml')).toBe(true);
  });

  test('an unrelated repo path does not register as relevant', () => {
    expect(RELEVANT_PATH_PATTERN.test('designs/agent-checkin.md')).toBe(false);
    expect(RELEVANT_PATH_PATTERN.test('agentforge4j-core/src/main/java/Foo.java')).toBe(false);
  });
});

test.describe('parseDirtyRelevantFiles — canonical dirty-tree detection, shared with site-provenance.mjs', () => {
  test('no output means nothing is dirty', () => {
    expect(parseDirtyRelevantFiles('')).toEqual([]);
    expect(parseDirtyRelevantFiles('   \n  ')).toEqual([]);
  });

  test('a tracked modification to a relevant file is detected', () => {
    const porcelain = ' M agentforge4j-ui-e2e/visual/manifest.ts\n';
    expect(parseDirtyRelevantFiles(porcelain)).toEqual(['agentforge4j-ui-e2e/visual/manifest.ts']);
  });

  test('a staged (added) relevant file is detected', () => {
    const porcelain = 'A  agentforge4j-web-ui/src/pages/NewPage.tsx\n';
    expect(parseDirtyRelevantFiles(porcelain)).toEqual(['agentforge4j-web-ui/src/pages/NewPage.tsx']);
  });

  test('an untracked (??) relevant file is detected — a new file never git-added is just as stale-making as a tracked edit', () => {
    const porcelain = '?? agentforge4j-ui-e2e/visual/new-entry.ts\n';
    expect(parseDirtyRelevantFiles(porcelain)).toEqual(['agentforge4j-ui-e2e/visual/new-entry.ts']);
  });

  test('an irrelevant dirty file is NOT detected', () => {
    const porcelain = ' M designs/agent-checkin.md\n?? README.notes.txt\n';
    expect(parseDirtyRelevantFiles(porcelain)).toEqual([]);
  });

  test('a mix of relevant and irrelevant dirty files returns only the relevant ones', () => {
    const porcelain = [
      ' M agentforge4j-ui-e2e/visual/manifest.ts',
      ' M designs/agent-checkin.md',
      '?? agentforge4j-web-ui/src/copy/new.ts',
      '?? scratch-notes.txt',
    ].join('\n');
    expect(parseDirtyRelevantFiles(porcelain)).toEqual([
      'agentforge4j-ui-e2e/visual/manifest.ts',
      'agentforge4j-web-ui/src/copy/new.ts',
    ]);
  });

  test('a rename of a relevant file keeps the new path', () => {
    const porcelain = 'R  agentforge4j-ui-e2e/visual/old-name.ts -> agentforge4j-ui-e2e/visual/new-name.ts\n';
    expect(parseDirtyRelevantFiles(porcelain)).toEqual(['agentforge4j-ui-e2e/visual/new-name.ts']);
  });
});

test.describe('parseAttestation — never let a malformed attestation crash warning-only CI', () => {
  function validAttestationJson() {
    return JSON.stringify({ commitSha: 'a'.repeat(40), manifestHash: 'b'.repeat(64), overallStatus: 'pass' });
  }

  test('a well-formed attestation parses successfully', () => {
    const result = parseAttestation(validAttestationJson());
    expect(result.ok).toBe(true);
    expect(result.attestation.overallStatus).toBe('pass');
  });

  test('malformed JSON is rejected, not thrown', () => {
    const result = parseAttestation('{ this is not valid JSON');
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('not valid JSON');
  });

  test('an empty file is rejected, not thrown', () => {
    const result = parseAttestation('');
    expect(result.ok).toBe(false);
  });

  test('valid JSON that is not an object (null) is rejected', () => {
    const result = parseAttestation('null');
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('does not contain a JSON object');
  });

  test('valid JSON that is an array is rejected', () => {
    const result = parseAttestation('[1, 2, 3]');
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('does not contain a JSON object');
  });

  test('valid JSON that is a bare string is rejected', () => {
    const result = parseAttestation('"just a string"');
    expect(result.ok).toBe(false);
  });

  test('missing commitSha is rejected', () => {
    const result = parseAttestation(JSON.stringify({ manifestHash: 'b'.repeat(64), overallStatus: 'pass' }));
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('commitSha');
  });

  test('missing manifestHash is rejected', () => {
    const result = parseAttestation(JSON.stringify({ commitSha: 'a'.repeat(40), overallStatus: 'pass' }));
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('manifestHash');
  });

  test('missing overallStatus is rejected', () => {
    const result = parseAttestation(JSON.stringify({ commitSha: 'a'.repeat(40), manifestHash: 'b'.repeat(64) }));
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('overallStatus');
  });

  test('an invalid field type (commitSha as a number) is rejected', () => {
    const result = parseAttestation(JSON.stringify({ commitSha: 12345, manifestHash: 'b'.repeat(64), overallStatus: 'pass' }));
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('commitSha');
  });

  test('an invalid field type (overallStatus as a boolean) is rejected', () => {
    const result = parseAttestation(JSON.stringify({ commitSha: 'a'.repeat(40), manifestHash: 'b'.repeat(64), overallStatus: true }));
    expect(result.ok).toBe(false);
    expect(result.warning).toContain('overallStatus');
  });
});
