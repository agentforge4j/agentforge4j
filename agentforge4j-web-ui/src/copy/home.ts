// SPDX-License-Identifier: Apache-2.0

export const HOME_COPY = {
  tagline: 'The embeddable Java framework for governed AI workflows.',
  intro:
    'AgentForge4j is an embeddable, open-source Java framework for designing and running ' +
    'human-in-the-loop AI workflows. Workflow structure, transitions, agents, prompts, and ' +
    'approval points can be defined externally — the runtime follows the orchestration you ' +
    'authored rather than inventing it at runtime.',
  whoItsFor: {
    heading: "Who it's for",
    body:
      'AgentForge4j is for Java teams building their own AI-powered business or engineering ' +
      'workflows — not simply using an off-the-shelf AI assistant. It is useful when AI must ' +
      'follow controlled steps, call internal tools, pause for human approval, retry safely, ' +
      'and leave an auditable record of what happened.',
    example:
      'Typical use cases include document review, compliance and onboarding processes, support ' +
      'triage, and internal engineering or security checks.',
  },
  pillars: [
    {
      heading: 'Predictable, not improvised',
      body:
        'The workflow author decides which agent runs at each step at design time. The runtime ' +
        'follows that authored orchestration rather than choosing agents dynamically, and every ' +
        'run is observable through the events and state it emits — though the LLM’s own ' +
        'output can still vary between runs.',
    },
    {
      heading: 'Externally defined, not hardcoded',
      body:
        'Workflow structure, transitions, agents, and system prompts can be defined in external ' +
        'JSON and Markdown files, so a workflow’s orchestration can change without changing ' +
        'code. Applications may still provide their own code-defined tools, integrations, ' +
        'providers, persistence, and extension implementations.',
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
    'Framework 0.1.0 and Workflow Catalog 0.1.0 are publicly available. The project remains ' +
    'pre-1.0 and under active development; see the catalogue for what ships today.',
} as const;
