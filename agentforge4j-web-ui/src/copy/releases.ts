// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const RELEASES_COPY = {
  intro:
    'AgentForge4j has not published a 0.1.0 release to Maven Central yet — there is nothing to ' +
    'install as a dependency today.',
  tracks: [
    { name: 'Framework', publishesTo: 'Maven Central' },
    { name: 'Shipped workflow catalog', publishesTo: 'Maven Central' },
    { name: 'Workflow builder', publishesTo: 'npm' },
  ],
  tracksIntro: 'Once released, AgentForge4j publishes three independently versioned tracks:',
  links: [
    { label: 'Tags', href: `${GITHUB_URL}/tags` },
    { label: 'Commit history', href: `${GITHUB_URL}/commits/main` },
  ],
} as const;
