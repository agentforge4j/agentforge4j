// SPDX-License-Identifier: Apache-2.0

export const CATALOGUE_COPY = {
  listHeading: 'Workflow catalogue',
  listIntro:
    'Browse the shipped, ready-to-run workflows published in the Workflow Catalog ' +
    '(the agentforge4j-workflows-catalog artifact).',
  emptyState: 'No workflows are published yet.',
  shippedBadge: 'Shipped',
  backToList: '← Back to catalogue',
  authorLabel: 'Author',
  contactLabel: 'Contact',
  versionLabel: 'Version',
  graphHeading: 'Step graph',
  graphAltText: (name: string) => `Step graph for the ${name} workflow`,
  openInBuilder: 'Open the Builder',
  openInBuilderNote:
    "Opens a new, empty workflow in the Builder — this catalogue workflow isn't loaded " +
    'automatically yet.',
} as const;
