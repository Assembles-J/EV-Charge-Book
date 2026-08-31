# EV Charge Book Branch and Pull Request Governance

Updated: 2026-08-31
Status: Repository governance authority
Owner: #6 documentation/status governance; #75 repository protection

## 1. Purpose

Keep a high-frequency repository understandable and safe without adding heavy process.

This document governs:

- branch lifecycle;
- pull request authority and status;
- stacked PR behavior;
- stale-branch cleanup;
- relationship between CI and physical acceptance;
- target `main` protection policy.

It does not define product behavior.

## 2. Authority rules

1. current `main` implementation/schema/runtime facts win over stale prose;
2. merged PR evidence is implementation history, not physical acceptance;
3. Draft/unmerged PR is never runtime authority;
4. Open Issue does not imply missing implementation;
5. implementation-complete Issues must close or explicitly become physical-acceptance owners;
6. historical branches and closed PRs must not be used as the source for new implementation work without checking current `main`.

## 3. Branch lifecycle

### Active branches

A remote branch is active only when at least one is true:

- it is `main`;
- it is the head/base of an Open PR;
- it contains explicitly preserved unpublished work with a current owning Issue;
- it is a temporary release/operations branch with a documented current purpose.

### Cleanup candidates

A branch is a cleanup candidate when all are true:

- its PR is merged or closed, or it was a temporary/no-op branch;
- no Open PR currently depends on it as head or base;
- it is not `main` or another explicitly protected branch;
- it has no unique unpublished work that an owning Issue still requires.

Examples include old `docs/*`, historical `feat/*`, `fix/*`, `ui/*`, `noop*` and `tmp-*` heads whose work already landed or was superseded.

### Cleanup timing

- merged PR head: delete after merge once no stacked PR depends on it;
- closed/superseded PR head: delete after the closure is documented and no recovery need exists;
- temporary/no-op branch: delete immediately after purpose is complete;
- stacked parent branch: retain until all dependent stacked PRs are retargeted or closed.

Git history remains available through merged commits/PRs; long-lived abandoned remote branches are not documentation.

## 4. Automatic branch deletion target

Repository target setting:

- enable `delete_branch_on_merge`.

Exception: if a merged branch is still the base of an active stacked PR, GitHub/maintainer must first retarget the child PR to the appropriate surviving base.

A repository setting must never be documented as enabled until the repository metadata confirms it.

## 5. Pull request minimum contract

Every non-trivial PR should identify:

- owning Issue;
- current baseline / base branch;
- what this PR changes;
- what it intentionally does not change;
- test/CI evidence required for this slice;
- physical acceptance owner when applicable;
- documentation/authority impact;
- superseded PR/Issue/document when applicable.

For docs-only PRs, runtime testing may be `Not applicable`, but authority impact must still be explicit.

## 6. Draft vs Ready

Use Draft when:

- scope or contract is still changing;
- required implementation slice is incomplete;
- the PR is a stacked child waiting for an unstable parent;
- the author explicitly does not want merge review yet.

Mark Ready only when:

- the PR's own stated scope is complete;
- its current head has the required automated evidence;
- the base relationship is intentional and current;
- no known unresolved blocker remains for that slice.

`Ready` still does not mean physical acceptance is complete.

## 7. Stacked PR rules

Stacked PRs are allowed, but dependency must be explicit.

Example:

```text
main
  <- PR A / branch-a
       <- PR B / branch-b
```

Rules:

1. PR B must state that it is stacked on PR A and name the parent/base.
2. Review PR A before PR B unless the review explicitly understands the combined diff.
3. Merge PR A first.
4. After PR A lands, retarget PR B to `main` (or the next intentional surviving parent).
5. Re-read PR B's changed files/diff after retarget; do not assume the old stacked diff equals the new main diff.
6. Re-run/confirm required CI for PR B's current head/base state.
7. Delete the parent branch only after dependent children are retargeted.
8. Never merge a child merely because the parent CI was Green.

## 8. CI evidence

Automated evidence and device acceptance are separate.

For Android/runtime code:

- required build/test checks must pass on the PR's current head;
- a stale Green run from an older head is supporting history, not current merge evidence;
- branch synchronization that changes effective code requires current-head evidence;
- docs-only changes may intentionally skip Android CI when workflow path filters do so.

Physical acceptance remains in its owning Issue and cannot be inferred from CI.

## 9. Target `main` protection

Repository target policy, owned by #75:

- protect `main`;
- require pull requests for normal changes;
- require the applicable Android Build check before runtime-code merge;
- require or otherwise verify that the checked PR head includes current `main` before merge;
- keep emergency admin bypass deliberate and exceptional;
- do not force unrelated Android checks on docs-only PRs when path filters intentionally skip them.

Until repository metadata confirms these controls, #75 remains Open.

## 10. Repository hygiene snapshot

The 2026-08-31 governance audit enumerated 192 remote branch refs across two result pages, including many branches for already merged/closed work. Repository metadata also reported `delete_branch_on_merge = false`.

This is a cleanup signal, not permission to bulk-delete blindly. The safe cleanup process is:

1. enumerate current Open PR head/base branches;
2. preserve `main` and all active dependency branches;
3. map remaining branches to merged/closed PRs where possible;
4. delete obvious merged/superseded/temp branches first;
5. review branches with no PR mapping for unique unpublished commits before deletion;
6. enable merge-time branch deletion after repository settings allow it.

## 11. Naming guidance

Prefer short purpose-oriented names:

- `feat/<area>-<purpose>`
- `fix/<area>-<purpose>`
- `docs/<purpose>`
- `chore/<purpose>`
- `ui/<area>-<purpose>`

Avoid permanent `tmp-*`, `noop*`, repeated `-final`, `-final2`, `-v2` chains. If experimentation requires them, remove them after the owning PR/work is resolved.

## 12. Closeout rule

Repository governance is healthy when:

- `main` protection is verifiably enabled;
- required current-head CI cannot be bypassed accidentally for runtime code;
- merged branches are normally removed;
- active stacked PR dependencies are visible and current;
- README points to current authority instead of duplicating an implementation checklist;
- old branches, Issues and Draft PRs cannot plausibly be mistaken for current implementation authority.
