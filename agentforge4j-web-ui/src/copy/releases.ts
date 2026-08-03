// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const RELEASES_COPY = {
  intro:
    'AgentForge4j publishes three independently versioned tracks. Framework 0.1.0 and Workflow ' +
    'Catalog 0.1.0 are both published; the Workflow Builder ships more frequently on npm.',
  tracksIntro: 'Release tracks:',
  tableHeadings: ['Track', 'Current version', 'Destination', 'Release'],
  tracks: [
    {
      name: 'Framework',
      version: '0.1.0',
      publishesTo: 'Maven Central',
      coordinates: 'org.agentforge4j:agentforge4j-bootstrap:0.1.0',
      mavenHref: 'https://central.sonatype.com/artifact/org.agentforge4j/agentforge4j-bootstrap',
      releaseLabel: 'framework-v0.1.0',
      releaseHref: `${GITHUB_URL}/releases/tag/framework-v0.1.0`,
    },
    {
      name: 'Workflow Catalog',
      version: '0.1.0',
      publishesTo: 'Maven Central',
      coordinates: 'org.agentforge4j:agentforge4j-workflows-catalog:0.1.0',
      mavenHref: 'https://central.sonatype.com/artifact/org.agentforge4j/agentforge4j-workflows-catalog',
      releaseLabel: 'catalog-v0.1.0',
      releaseHref: `${GITHUB_URL}/releases/tag/catalog-v0.1.0`,
    },
    {
      name: 'Workflow Builder',
      version: '0.6.1',
      publishesTo: 'npm',
      coordinates: '@agentforge4j/workflow-builder-react',
      mavenHref: 'https://www.npmjs.com/package/@agentforge4j/workflow-builder-react',
      releaseLabel: null,
      releaseHref: null,
    },
  ],
  links: [
    { label: 'Tags', href: `${GITHUB_URL}/tags` },
    { label: 'Commit history', href: `${GITHUB_URL}/commits/main` },
  ],
} as const;
