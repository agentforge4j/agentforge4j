# Recruitment Workflow — Example Structured State

This document shows the shape of every key context value produced and consumed by the recruitment workflow. Use it to validate agent outputs and to wire UIs.

---

## `recruitmentProfile`

The canonical, finalised profile. Produced by `recruitment-profile-finalizer-agent`.

```json
{
  "roleTitle": "Senior Java Backend Engineer",
  "seniority": "senior",
  "department": "Core Banking Platform",
  "function": "engineering",
  "specialisation": "Java backend, distributed systems",
  "employmentType": "full-time",
  "location": {
    "type": "hybrid",
    "city": "Frankfurt",
    "country": "Germany",
    "timezone": "Europe/Berlin"
  },
  "compensation": {
    "currency": "EUR",
    "min": 95000,
    "max": 130000,
    "bonusOrEquity": "10% target bonus, no equity"
  },
  "mustHaveSkills": ["Java 21", "Spring Boot", "Kafka", "PostgreSQL", "Distributed systems"],
  "niceToHaveSkills": ["Kubernetes", "GraphQL", "Reactive programming"],
  "yearsExperienceMin": 7,
  "yearsExperienceMax": null,
  "languages": [
    { "language": "English", "level": "C1+" },
    { "language": "German", "level": "B2 preferred" }
  ],
  "industryContext": "banking",
  "complianceRequirements": [
    "Background and credit check (BaFin-aligned)",
    "Production access vetting",
    "PSD2 awareness"
  ],
  "teamContext": "10-engineer payments platform team, on-call rotation",
  "responsibilities": [
    "Own service design and delivery for new payment flows",
    "Mentor mid-level engineers",
    "Participate in architecture review board",
    "Lead incident response in on-call rotation"
  ],
  "interviewProcess": [
    "Technical screen",
    "Take-home assessment",
    "System design interview",
    "Hiring manager and team panel",
    "Compliance interview"
  ],
  "urgency": "Q3 start preferred",
  "businessJustification": "Replacing departing tech lead; backlog growth requires senior capacity",
  "_meta": {
    "createdAt": "2026-04-30T10:14:00Z",
    "version": 1
  },
  "_warnings": []
}
```

---

## `candidates` (array)

Produced incrementally by `recruitment-cv-analysis-agent`, mutated by `recruitment-ranking-agent` and `recruitment-assessment-evaluator-agent`.

```json
[
  {
    "candidateId": "cand-001",
    "name": "Jane Doe",
    "contact": { "email": "jane.doe@example.com", "phone": "+49 ..." },
    "yearsExperience": 9,
    "seniorityAssessment": "senior",
    "skillsEvidenced": ["Java 17", "Spring Boot", "Kafka", "PostgreSQL", "AWS"],
    "skillsMissing": ["Java 21 (only Java 17 evidenced)", "PSD2 specifics"],
    "employmentHistory": [
      {
        "company": "FintechCo",
        "role": "Senior Backend Engineer",
        "duration": "2021-present",
        "highlights": "Led migration of payment ledger to Kafka-based event sourcing"
      }
    ],
    "education": [
      { "institution": "TU Munich", "qualification": "MSc Computer Science", "year": "2015" }
    ],
    "certifications": ["AWS Solutions Architect Associate"],
    "languages": [
      { "language": "English", "level": "C1" },
      { "language": "German", "level": "Native" }
    ],
    "redFlags": [],
    "scores": {
      "mustHaveCoverage": 80,
      "niceToHaveCoverage": 33,
      "experienceFit": 95,
      "domainFit": 85,
      "complianceFit": 60,
      "redFlagPenalty": 0
    },
    "overallScore": 79,
    "rationale": {
      "strengths": [
        "9 years' experience exceeds the 7-year minimum",
        "Direct fintech background with payment ledger migration on Kafka"
      ],
      "concerns": [
        "Java 21 not explicitly evidenced (Java 17 is)",
        "Limited PSD2 specifics in CV"
      ],
      "scoringExplanation": "mustHave 80 × 0.35 + experience 95 × 0.20 + domain 85 × 0.20 + niceToHave 33 × 0.10 + compliance 60 × 0.15 = 79.05 → 79."
    },
    "stage": "cv-analysed",
    "analysedAt": "2026-04-30T10:30:00Z",

    "rank": 1,

    "assessmentResult": {
      "assessmentId": "assess-cand-001",
      "scoredAt": "2026-05-05T15:00:00Z",
      "criteriaScores": [
        { "criterion": "Correctness", "score": 9, "weight": 0.30, "rationale": "All acceptance criteria met for tasks 1 and 2; task 3 implements the happy path with one edge case missed." },
        { "criterion": "Code quality / clarity", "score": 8, "weight": 0.25, "rationale": "Idiomatic Java, clean module boundaries; some methods exceed 50 lines." },
        { "criterion": "Design / trade-off awareness", "score": 9, "weight": 0.25, "rationale": "Design doc explicitly weighs CRDT vs leader election; chose appropriate option for stated constraints." },
        { "criterion": "Testing / verification", "score": 7, "weight": 0.20, "rationale": "Unit tests present for core flow; integration test missing for the Kafka boundary." }
      ],
      "assessmentScore": 83,
      "summary": "Strong submission. Demonstrates senior-level design instincts and pragmatic implementation. Minor gaps in test coverage and code length discipline.",
      "strengths": ["Trade-off articulation", "Idiomatic Spring Boot", "Clear design doc"],
      "weaknesses": ["Test coverage at integration boundary", "Long methods"],
      "interviewFocusAreas": [
        "Probe edge case handling under load",
        "Discuss strategy for evolving the chosen design when scale doubles"
      ]
    },
    "combinedScore": 81
  }
]
```

---

## `shortlistedCandidates` (array)

Subset of `candidates` selected by `recruitment-ranking-agent`. Same schema; iterated by the `assessment-per-candidate` blueprint via `FOR_EACH`.

---

## `rejectedCandidates` (array)

Subset of `candidates` not shortlisted. Each entry adds a `rejectionReason`:

```json
{
  "candidateId": "cand-007",
  "name": "John Smith",
  "rank": 8,
  "overallScore": 42,
  "rejectionReason": {
    "summary": "Insufficient distributed-systems experience for a senior banking platform role.",
    "primaryGap": "must-have-skills",
    "evidence": [
      "CV lists 4 years of total backend experience; minimum required is 7.",
      "No evidence of Kafka or any messaging/streaming platform.",
      "No exposure to regulated financial systems."
    ],
    "missingMustHaves": ["Kafka", "Distributed systems"],
    "alignedAreas": ["Java fundamentals", "Spring Boot at small scale"],
    "suggestedAlternative": "Better suited for a mid-level role on a non-regulated team."
  }
}
```

---

## `currentAssessment` (per-iteration of FOR_EACH)

Set by `recruitment-assessment-generator-agent`. Cleared/replaced each iteration.

```json
{
  "assessmentId": "assess-cand-001",
  "candidateId": "cand-001",
  "function": "engineering",
  "seniority": "senior",
  "title": "Distributed payment idempotency service",
  "estimatedEffortHours": 5,
  "context": "You are designing a service that ensures payment requests are processed at most once across regional outages...",
  "tasks": [
    {
      "taskId": "task-1",
      "title": "Implement the idempotency core",
      "description": "Build a Spring Boot service exposing POST /payments with the following semantics...",
      "deliverable": "Git repository link with README",
      "acceptanceCriteria": [
        "Duplicate requests with the same idempotency key return the original response",
        "Service handles concurrent duplicates without race conditions",
        "Storage layer is pluggable"
      ]
    },
    {
      "taskId": "task-2",
      "title": "Design doc",
      "description": "Produce a 2-page design doc covering trade-offs of your chosen storage and concurrency strategy...",
      "deliverable": "Markdown file in the repo",
      "acceptanceCriteria": [
        "Explicitly addresses regional outage handling",
        "Compares at least two alternatives with reasoning"
      ]
    }
  ],
  "evaluationRubric": [
    { "criterion": "Correctness", "weight": 0.30, "scale": "0–10" },
    { "criterion": "Code quality / clarity", "weight": 0.25, "scale": "0–10" },
    { "criterion": "Design / trade-off awareness", "weight": 0.25, "scale": "0–10" },
    { "criterion": "Testing / verification", "weight": 0.20, "scale": "0–10" }
  ],
  "submissionInstructions": "Reply to this email with the repo link and any access notes.",
  "timeLimit": "5 working days from receipt"
}
```

---

## `finalSelection`

Final output of the workflow. Produced by `recruitment-final-selection-agent`.

```json
{
  "generatedAt": "2026-05-10T09:00:00Z",
  "roleTitle": "Senior Java Backend Engineer",
  "rankedCandidates": [
    {
      "rank": 1,
      "candidateId": "cand-001",
      "name": "Jane Doe",
      "overallScore": 79,
      "assessmentScore": 83,
      "combinedScore": 81,
      "headline": "Strongest end-to-end fit: deep payment-ledger background and senior-level design instincts.",
      "interviewFocusAreas": [
        "Probe Java 21-specific features",
        "Discuss PSD2 production access expectations"
      ],
      "openConcerns": [
        "Java 21 not explicitly evidenced — confirm in technical interview",
        "Integration test coverage gap in submission"
      ]
    }
  ],
  "summary": "## Final Selection Summary\n\nThree candidates evaluated through assessment...\n\n### Recommended interview order\n1. Jane Doe — strongest combined score; lead with system design panel.\n2. ...\n"
}
```

---

## Scoring Model Reference

```
overallScore (CV) =
    mustHaveCoverage  × 0.35
  + experienceFit     × 0.20
  + domainFit         × 0.20
  + niceToHaveCoverage× 0.10
  + complianceFit     × 0.15
  − redFlagPenalty

If complianceRequirements is empty, redistribute:
  mustHave 0.40, experience 0.25, domain 0.25, niceToHave 0.10

assessmentScore = Σ(criterionScore × weight × 10)
combinedScore   = round(0.5 × overallScore + 0.5 × assessmentScore)
```

---

## Internal/Transient Keys

These appear in the workflow context but are not part of the durable output:

| Key | Producer | Consumer | Lifecycle |
|---|---|---|---|
| `rolePrompt` | initial-role-input | intake loop | persists |
| `recruitmentProfileDraft` | intake agent | profile finalizer | superseded by `recruitmentProfile` |
| `intakeUserResponse` | user input | intake agent | per-iteration |
| `cvText`, `candidateName`, `moreCvs` | cv-upload artifact | cv-analysis, coordinator | per-iteration |
| `jobPostChannel` | job-post-target artifact | job post agent | persists |
| `jobPost` | job post agent | publisher, approval gate | persists |
| `jobPostApproved`, `profileConfirmed`, `shortlistConfirmed` | confirmation artifacts | branch gates | per-decision |
| `shortlistSize` | shortlist-config artifact | ranking agent | persists |
| `currentCandidate` | FOR_EACH binding | assessment generator/evaluator | per-iteration |
| `currentAssessment` | assessment generator | submission/evaluator | per-iteration |
| `submission`, `submissionReceived` | assessment-submission artifact | evaluator | per-iteration |
| `__profileConfirmedMarker` | RESOURCE no-op | none | placeholder |
