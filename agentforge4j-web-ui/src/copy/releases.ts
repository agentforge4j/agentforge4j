// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const RELEASES_COPY = {
  intro:
    'AgentForge4j 0.1.0, the first public framework release, is published to Maven Central.',
  tracks: [
    { name: 'Framework', publishesTo: 'Maven Central' },
    { name: 'Shipped workflow catalog', publishesTo: 'Maven Central' },
    { name: 'Workflow builder', publishesTo: 'npm' },
  ],
  tracksIntro: 'AgentForge4j publishes three independently versioned tracks:',
  links: [
    { label: 'framework-v0.1.0 release notes', href: `${GITHUB_URL}/releases/tag/framework-v0.1.0` },
    { label: 'Framework on Maven Central', href: 'https://central.sonatype.com/namespace/org.agentforge4j' },
    { label: 'Tags', href: `${GITHUB_URL}/tags` },
    { label: 'Commit history', href: `${GITHUB_URL}/commits/main` },
  ],
} as const;
