// SPDX-License-Identifier: Apache-2.0

export const CATALOGUE_COPY = {
  listHeading: 'Workflow catalogue',
  listIntro: 'Shipped, ready-to-run workflows from the agentforge4j-workflows-catalog module.',
  emptyState: 'No workflows are published yet.',
  shippedBadge: 'Shipped',
  backToList: '← Back to catalogue',
  authorLabel: 'Author',
  contactLabel: 'Contact',
  versionLabel: 'Version',
  graphHeading: 'Step graph',
  graphAltText: (name: string) => `Step graph for the ${name} workflow`,
  openInBuilder: 'Open in Builder',
} as const;
