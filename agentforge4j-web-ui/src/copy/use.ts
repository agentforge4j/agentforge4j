// SPDX-License-Identifier: Apache-2.0

export const USE_COPY = {
  intro:
    'AgentForge4j is a set of Java libraries you embed in your own application — there is no ' +
    'server to install and no account to create.',
  groupId: 'org.agentforge4j',
  version: '0.1.0',
  primaryArtifactId: 'agentforge4j-bootstrap',
  requirements: [
    'Java 17 or later to embed the framework.',
    'Your own LLM provider credentials — AgentForge4j calls the provider you configure; it does ' +
      'not include one.',
    'Your own persistence, if you need runs to survive a restart — implement the ' +
      'state-repository contract for whatever storage you use.',
  ],
  embedSteps: [
    {
      heading: 'Add the framework',
      body: 'Depend on agentforge4j-core and agentforge4j-runtime.',
    },
    {
      heading: 'Bring your own LLM provider',
      body:
        'Implement LlmClientFactory and register it via ServiceLoader, or use one of the bundled ' +
        'providers.',
    },
    {
      heading: 'Author a workflow',
      body:
        'Define agents and steps in external JSON/Markdown — nothing about a specific workflow is ' +
        'hardcoded in Java.',
    },
    {
      heading: 'Run it',
      body:
        'The runtime executes the workflow you authored, persists state, and emits events at each ' +
        'transition.',
    },
  ],
} as const;
