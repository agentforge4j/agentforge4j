# Human Approval

> **Status: implementation pending (Batch 2).** This README describes the intended example; the
> Maven module and sources land in Batch 2.

## What this teaches

How a workflow suspends to wait for a human decision and then resumes — the core of any
human-in-the-loop process. The example pauses at an approval point, then continues once the
decision is supplied.

## AgentForge4j capability demonstrated

Workflow suspend/resume: the run reaches an `AWAITING_*` state and is driven forward by a resume
call. (The exact suspend kind and resume verb are finalized against live source in Batch 2.)

## How to run

```bash
./mvnw -pl framework-examples/human-approval -am verify
```

(plus the `main()` entry point, once implemented).

## Expected behaviour / output

The workflow suspends at the approval gate, then — once approval is supplied — resumes and reaches
its terminal `WorkflowStatus`; the bundled test asserts both the suspended and terminal states
deterministically.

## Files to read first

*Pending implementation (Batch 2).*
