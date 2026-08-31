# EV Charge Book Current Status Authority

Updated: 2026-08-31
Status: Operational status authority
Baseline: `main@e5149a6474a17c7a5ce0a987f04fc3ab38a143b0`

## Purpose

This file owns **fast-changing project execution status**. Stable product and architecture principles remain in `PROJECT_MASTER.md` and domain-specific authority documents.

When status sources disagree, use this order:

1. current `main` implementation facts and persisted schemas;
2. merged PR / current-head CI evidence;
3. this current-status authority;
4. owning Open Issue for remaining acceptance or future work;
5. older roadmap/history/versioned design text.

An Open Issue does not imply missing code. A Draft/unmerged PR is not runtime authority. CI Green is not physical acceptance.

## Current repository governance

### Implemented governance baseline

Merged PR #263 established:

- root README as a stable project entrypoint instead of a stale pre-Room/MVP checklist;
- root MIT `LICENSE`;
- `.github/PULL_REQUEST_TEMPLATE.md`;
- evidence-first Bug / Feature / Documentation-Governance Issue templates;
- `docs/BRANCH_AND_PR_GOVERNANCE.md` for branch lifecycle, stacked PRs and merge evidence.

### Remaining repository-setting gaps

Current repository metadata still reports:

- `main` not branch-protected;
- required status checks empty;
- repository rulesets empty;
- `delete_branch_on_merge = false`.

The 2026-08-31 branch audit returned 192 remote branch refs across two pages.

Ownership:

- #75 — `main` protection + current-head required Android CI policy;
- #265 — stale remote branch cleanup + merge-time branch deletion.

The connected GitHub tool can read these states but currently exposes neither branch-protection/ruleset writes nor remote-ref deletion. #75/#265 must not be closed until GitHub metadata itself confirms the change.

## Active delivery streams

### Charging v0.7 — parent #251

Current product/data authority:

- #251 parent workflow and truth rules;
- `CHARGING_V0.7_DESIGN_AND_IMPLEMENTATION_PLAN.md`;
- #252 coupled calculation / derived metrics;
- #260 Add/Edit linked billing adoption;
- #253 optional active charging session / preset lifecycle;
- #254 current-location behavior / future map-point picker.

#### PR #258 — pure calculation contract

Current parent state at this snapshot:

- Draft;
- current head `b506b749a6cea7041b4825b80b83ed9455b4196e`;
- comparison to baseline `main@e5149a6` is **ahead 10 / behind 0**;
- effective diff remains only `ChargeCalculationEngine.kt` + focused test;
- prior head Android Build #626 was Green;
- synchronized-head Android Build #637 is running at the time of this snapshot.

Durable merge gate: the current synchronized head needs successful current-head Android CI and unchanged intended diff before Ready/Merge. Historical #626 Green does not authorize the new head.

#### PR #261 — stacked Add/Edit billing adoption

Current child state:

- Draft;
- child head `c805ba3150576b57dc2cd74631edc09e0a8b8c44`;
- base branch name is still the #258 calculation branch;
- the child was built against the older #258 parent head;
- Android Build #630 was Green for that older stack relationship;
- after #258 synchronized to current `main`, comparison to the **new parent branch** is now diverged/behind;
- intended child diff remains editor state/tests plus `AddRecordScreen.kt` and `RecordEditScreen.kt` adoption.

Required order:

1. finish current-head validation/review for #258;
2. resolve/merge #258 first;
3. normalize #261 onto the surviving `main` base using the normal code-maintenance workflow;
4. re-read #261 effective diff;
5. obtain current-head Android CI for the normalized child;
6. only then decide Ready/Merge for #261.

Do not delete the #258 branch while #261 still depends on it.

### Vehicle maturity / catalog / resource onboarding — #244 and #20

Already in `main` before #264:

- user vehicle nickname and nickname-first display;
- managed-catalog-only primary add flow;
- standard vehicle facts read-only in Android;
- managed brand metadata / stable `brandId`;
- managed Light/Dark Brand Logo publishing and Android cached rendering;
- range-standard metadata;
- catalog JSON/CSV import/export and template;
- managed Hero semantic key selection;
- batch Brand Logo / Hero helpers;
- copyable Logo/Hero standards and prompt center.

Merged PR #264 further advanced the resource workflow:

- unified `车型资源工作台` for one-vehicle resource-bundle onboarding;
- one resource-bundle JSON carrying one managed brand + one vehicle plus coordinated assets;
- mixed Logo/Hero multi-file queue with automatic matching as suggestion only and explicit manual correction;
- duplicate/shared Hero-key review with explicit update confirmation;
- all-in-one full asset-bundle prompt while retaining the single-item prompt library;
- stable base `heroArtworkKey` with published `<base>-dark` / `<base>-light` variants;
- Android Hero resolution with backward-compatible fallback:
  - Dark: `<base>-dark -> legacy <base>`;
  - Light: `<base>-light -> <base>-dark -> legacy <base>`;
- old split batch Logo/Hero panels retired from the main admin UI; single-item maintenance remains;
- new `Admin Resource Workbench` CI contract.

Current product/admin authority: `RESOURCE_BUNDLE_WORKFLOW.md`, owner #244.

#244 owns remaining real workflow/device maturity, not implementation of another disconnected Logo/Hero/catalog onboarding system.

#20 remains the data-quality owner: provenance, normalization, conflicts/corrections, broader real-model coverage and coverage metrics. The unified one-vehicle workbench does not replace bulk catalog data-quality work.

### Trip background/location reliability — #77

Current `main` includes:

- #80 removal of old 8m callback displacement gate;
- #184 delayed-delivery grace while preserving original `Location.time` authority and 120s LONG_GAP;
- #217 Google Play Fused production source;
- #220 non-GMS platform GPS/network fallback;
- #226 ~1 Hz active Trip location target and faster stationary transition handling;
- #231 trusted-speed-first stationary distance handling;
- #234 silent-provider recovery with platform-provider restrictions and Google Fused watchdog fallback.

Truth boundaries remain:

- original `Location.time` is capture-time authority;
- no synthetic route points;
- `LONG_GAP_SECONDS = 120` is a hard continuity boundary;
- stationary GNSS drift must not become distance.

#77 remains Open for current-main physical lock-screen/background/provider-loss acceptance. Closed #203 no longer owns a missing Fused migration.

### Bluetooth-triggered Trip — #235

Already in `main`:

- #239 auto-trip mode/state/reason model + eligibility policy;
- #241 persisted per-vehicle Bluetooth detection sessions, dedupe, ignore handling and notification entry flow;
- #242 explicit per-vehicle Bluetooth auto-start option routed through `TripStartCoordinator` with location/notification/active-Trip guards.

#235 remains Open for OEM/background physical acceptance, trigger-quality policy, verified-movement evaluation and possible later parking-assistant work. Bluetooth connection alone is not proven driving evidence.

### Trip interaction / analysis

Already in `main`:

- route pan/pinch/reset and trusted-speed route encoding (#219/#221/#230);
- trend pan/zoom/tap and stock-style long-press drag inspection (#218/#227);
- smoother SOC wheel behavior (#230);
- uncertainty-aware altitude filtering (#228/#232);
- recent-completed-Trip consumption input for end-SOC estimator (#213);
- truthful trajectory playback engine/UI (#198/#201).

Remaining map work under #192/#199 is basemap/provider/context validation, road labels, licensing/attribution, mainland-China usefulness and truthful fallback — not basic pan/zoom.

### Updater

Updater runtime includes release discovery, DownloadManager, SHA-256, installer handoff, prompt dedupe and #247 persisted recovery of download/install-ready state across process restart.

#102 remains valid only as the production old-APK -> new-APK physical in-place-upgrade acceptance owner.

## GitHub Actions workflow ownership

Detailed trigger/ownership boundaries are documented in `WORKFLOW_OWNERSHIP.md`.

At this baseline there are **nine** workflow files:

- `android-build.yml` — Android PR/push validation + Debug APK artifact;
- `android-release.yml` — manual production signed APK publish path;
- `hero-admin.yml` — integrated resource-admin application/container/lifecycle validation;
- `hero-admin-deploy.yml` — production resource-admin deployment on qualifying `main` push;
- `hero-assets-publish.yml` — Hero asset package validation;
- `admin-resource-workbench.yml` — unified vehicle resource-bundle workbench contract validation;
- `admin-batch-image-upload.yml` — retained focused/legacy batch-helper regression contract, not preferred new-model onboarding authority;
- `admin-prompt-library.yml` — single-item + full-bundle prompt/index contract validation;
- `vehicle-catalog-admin-tools.yml` — catalog admin/import-export browser-contract validation.

These workflows overlap by paths intentionally but do not have the same responsibility. Validation, resource publication through existing admin endpoints and production admin deployment are distinct states.

## Authority maintenance rules

1. Update the owning Issue whenever implementation stage changes.
2. Close superseded implementation-only Issues when their code is merged and physical acceptance is owned elsewhere.
3. Keep physical-only Issues explicitly written as physical acceptance; do not leave stale implementation checklists.
4. Draft PRs may propose implementation/design but are not runtime authority.
5. `PROJECT_MASTER.md` owns stable architecture/product principles; this file owns fast-moving execution status.
6. `ROADMAP.md` owns milestone ordering. When its dated prose lags this file, this file wins for current execution state.
7. Historical design/reference documents remain useful only when their version/supersession boundary is explicit.
8. No broad reimplementation may start from an old Issue before checking current `main` and merged PR history.
9. Stacked parent branches remain until every Open child is safely retargeted.
10. Historical Green CI does not authorize merge after effective head/base changes.
11. When an admin surface is superseded, its old workflow must be explicitly treated as retained regression coverage or retired; existence of a workflow file does not make that old UI the current product authority.

## Current governance queue

Completed in the 2026-08-31 audit:

- #6 authority graph reconciled;
- #203/#214/#222/#223/#224/#225 stale implementation Issues closed;
- #77/#235/#244/#20/#192/#205/#102/#94/#215 rewritten to current responsibilities;
- stale Draft PR #236/#255 closed without merge;
- README/LICENSE/templates/branch-stack rules merged by #263;
- #75 rewritten to exact current branch-protection/CI target;
- #265 created for stale-branch cleanup/merge-time deletion;
- #258/#261/#260/#251 descriptions synchronized to actual stacked behavior;
- PR #264 resource workbench / Hero theme-variant baseline incorporated into current authority.

Remaining documentation debt after this authority-sync PR:

- reconcile older Trip/UI acceptance Issues that still call historical SHAs “latest main”;
- narrow old #70 local-Hero/model-whitelist wording to physical visual closeout;
- add explicit supersession/version notes to older docs that still look current.
