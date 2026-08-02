// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const COMMUNITY_COPY = {
  intro:
    'AgentForge4j is open source and welcomes contributions — bug reports, documentation ' +
    'improvements, and code.',
  ways: [
    'Report a bug or request a feature via an issue template.',
    'Improve the documentation.',
    'Submit code via a pull request.',
  ],
  status:
    'AgentForge4j is in active development and pre-1.0 — APIs and conventions may still change. ' +
    'For anything beyond a small fix, open an issue first so the approach can be agreed before ' +
    'you write code.',
  devSetup: [
    'Java 17 is the project baseline (CI also verifies the build on Java 21).',
    'The bundled Maven Wrapper builds the project — no separate Maven install needed.',
    'The TypeScript packages live outside the Maven reactor and use Node 24.',
  ],
  prWorkflow: [
    'Branch per change off main.',
    'Keep changes focused and additive — avoid unrelated refactors in the same PR.',
    'Add or update tests for any change in behaviour.',
    'Open a PR to main describing what changed and why.',
    'CI must be green before a PR can merge; maintainers review and merge.',
  ],
  links: [
    { label: 'Contributing guide', href: `${GITHUB_URL}/blob/main/CONTRIBUTING.md` },
    { label: 'Code of Conduct', href: `${GITHUB_URL}/blob/main/CODE_OF_CONDUCT.md` },
    { label: 'Issues', href: `${GITHUB_URL}/issues` },
    { label: 'Discussions', href: `${GITHUB_URL}/discussions` },
  ],
} as const;
