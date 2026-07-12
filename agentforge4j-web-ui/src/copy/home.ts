// SPDX-License-Identifier: Apache-2.0

export const HOME_COPY = {
  tagline: 'Structured multi-agent AI workflow orchestration for the Java ecosystem.',
  intro:
    'AgentForge4j is an embeddable, open-source Java framework for designing and running ' +
    'predictable, auditable, human-in-the-loop AI workflows. Workflows are fully defined in ' +
    'configuration — no hardcoded agents, no runtime surprises.',
  pillars: [
    {
      heading: 'Predictable, not improvised',
      body:
        'The workflow author decides which agent runs at each step at design time. The runtime ' +
        'executes that decision faithfully — it never selects agents dynamically. Every run is ' +
        'auditable, repeatable, and fully defined by its configuration.',
    },
    {
      heading: 'Everything is external configuration',
      body:
        'No agents, workflows, or steps are hardcoded in Java. Agent definitions, system prompts, ' +
        'and workflow definitions live in external JSON and Markdown files, so a workflow can ' +
        'change without touching code.',
    },
    {
      heading: 'Human approval where it matters',
      body:
        'Transition gates (automatic, human-review, or human-approval) sit between steps, so a ' +
        'workflow can pause for a real decision exactly where the author placed one — not ' +
        'everywhere, and not nowhere.',
    },
  ],
  status:
    'AgentForge4j is in active development, pre-1.0. Core modules are built and reviewed; see ' +
    'the catalogue for what runs today.',
} as const;
