// SPDX-License-Identifier: Apache-2.0

export const ARCHITECTURE_COPY = {
  intro:
    'AgentForge4j is a set of layered Java modules, not a monolith. The core module knows ' +
    'nothing about a specific LLM provider or transport; providers, tools, and configuration ' +
    'loading are separate modules built on top of it.',
  moduleOverview: {
    heading: 'Module structure',
    body:
      'Core has no dependency on the LLM SPI — it stays independent so the execution engine is ' +
      'never coupled to a specific provider. The runtime builds on core; config loading, tools, ' +
      'and Spring Boot integration are separate layers above that.',
  },
  runtimeOverview: {
    heading: 'How a run executes',
    body:
      'Each step resolves its next behaviour, runs it (an LLM call for agent-bearing steps, plain ' +
      'logic otherwise), passes through a transition gate (automatic, human-review, or ' +
      'human-approval), persists state, and emits events — repeating until the workflow reaches a ' +
      'terminal state.',
  },
  detailLink: {
    label: 'Full module dependency diagram',
    href: '/diagrams/oss-module-dependency.svg',
  },
} as const;
