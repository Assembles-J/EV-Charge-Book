---
name: Documentation / Governance
description: Reconcile stale authority, Issue/PR state, release evidence or repository hygiene
title: "docs: "
labels: ["documentation"]
assignees: []
---

## Governance problem

What source currently conflicts with project/repository facts?

## Current evidence

- Current `main` fact / repository metadata:
- Merged PR / CI evidence:
- Conflicting Issue / PR / document:

## Authority decision

Use the project precedence:

1. current `main` implementation/schema/runtime facts
2. merged PR + CI evidence
3. `docs/CURRENT_STATUS_AUTHORITY.md`
4. owning Open Issue for remaining work/acceptance
5. roadmap / historical design material

State the intended authority after reconciliation:


## Required maintenance

- [ ] stale implementation wording removed or version-bounded
- [ ] completed implementation Issue closed or rewritten as acceptance-only
- [ ] superseded Draft PR/document explicitly closed/marked
- [ ] current owner for remaining work is explicit
- [ ] CI is not presented as physical-device acceptance

## Scope boundary

This governance Issue does not authorize unrelated business-code implementation.

## Close condition

The same current stage is described by `main`, merged evidence, current authority docs and the owning Issue.
