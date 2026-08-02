// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const LEGAL_COPY = {
  license: {
    heading: 'Licence',
    body:
      'AgentForge4j is licensed under the Apache License 2.0. The licence covers the code; it ' +
      'does not grant rights to the AgentForge4j name or logo.',
    link: { label: 'Apache License 2.0', href: `${GITHUB_URL}/blob/main/LICENSE` },
  },
  privacy: {
    heading: 'Privacy',
    body:
      'This site does not use analytics, tracking scripts, or cookies of its own, and does not ' +
      'collect personal data. It links out to GitHub for issues, discussions, and the source ' +
      "code — those pages are covered by GitHub's own privacy policy, not this one.",
  },
  accessibility: {
    heading: 'Accessibility',
    body:
      'This site aims for a solid accessibility baseline: a skip-to-content link, visible ' +
      'keyboard-focus indicators, one heading per page, keyboard-operable navigation, and ' +
      'respect for reduced-motion preferences. This is a baseline, not a certified WCAG ' +
      'conformance claim — if you hit an accessibility problem, please report it.',
  },
  trademark: {
    heading: 'Trademark and logo',
    body:
      'AgentForge4j is an independent open-source project. The AgentForge4j name and logo ' +
      'identify the project; please do not use them in a way that suggests official ' +
      'endorsement or affiliation without permission.',
  },
} as const;
