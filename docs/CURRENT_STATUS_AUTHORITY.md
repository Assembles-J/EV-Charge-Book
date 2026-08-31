# EV Charge Book Current Status Authority

Updated: 2026-08-31
Status: Operational status authority
Baseline: `main@81b50a5b15408bac7bb998041d0681426c829c61`

## Purpose

This file owns **fast-changing project execution status**. Stable product and architecture principles remain in `PROJECT_MASTER.md` and the domain-specific authority documents.

When status sources disagree, use this order:

1. current `main` implementation facts and persisted schemas;
2. merged PR / CI evidence;
3. this current-status authority;
4. owning Open Issue for remaining acceptance or future work;
5. older roadmap/history/versioned design text.

An Open Issue does not imply missing code. A Draft/unmerged PR is not runtime authority. CI Green is not physical acceptance.

## Current repository governance

### Implemented governance baseline

Merged PR #263 established:

- current root README as a stable project entrypoint instead of a stale MVP checklist;
- root MIT `LICENSE` matching the repository's declared license intent;
- `.github/PULL_REQUEST_TEMPLATE.md`;
- evidence-first Bug / Feature / Documentation-Governance Issue templates;
- `docs/BRANCH_AND_PR_GOVERNANCE.md` for branch lifecycle, stacked PRs and merge evidence.

### Remaining repository-setting gaps

- `main` is still not branch-protected;
- required status checks are still empty;
- repository rulesets are empty;
- `delete_branch_on_merge` is still false;
- the 2026-08-31 branch audit returned 192 remote branch refs across two pages.

Ownership:

- #75 — `main` protection + current-head required Android CI policy;
- #265 — stale remote branch cleanup + merge-time branch deletion.

The current ChatGPT GitHub connection can read these repository states but does not expose ruleset/protection writes or remote-ref deletion. #75/#265 must not be closed until GitHub metadata itself confirms the settings/cleanup.

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

Current status:

- Draft;
- head `2d9de0c666f56bc5b0e5f56e71afce0f1763eda0`;
- only engine + focused test files in its own diff;
- Android Build #626 Green on that head;
- current repository comparison shows the branch is **10 commits behind current `main`**.

Therefore the old “behind by 0” claim is stale. #258 must synchronize with current `main`, re-check its effective diff and obtain current-head CI after synchronization before Ready/Merge.

#### PR #261 — stacked Add/Edit billing adoption

Current status:

- Draft;
- base = #258 branch;
- head `c805ba3150576b57dc2cd74631edc09e0a8b8c44`;
- 7 commits ahead / 0 behind its current #258 parent;
- Android Build #630 Green on the stacked head;
- actual diff includes `ChargeBillingEditorState`, focused tests, `AddRecordScreen.kt` adoption and `RecordEditScreen.kt` adoption.

The earlier description that #261 did not yet modify Add/Edit forms was stale and has been corrected.

Required order:

1. synchronize/revalidate #258 against current `main`;
2. review/merge #258 first;
3. retarget #261 to `main`;
4. re-read #261 changed files/diff against `main`;
5. obtain current-head Android CI after effective base/head changes;
6. only then decide Ready/Merge for #261.

Do not delete the #258 branch while #261 depends on it.

### Vehicle maturity / catalog — #244 and #20

Already in `main`:

- user vehicle nickname and nickname-first display;
- managed-catalog-only primary add flow;
- standard vehicle facts read-only in Android;
- removal of Android supported-model hard-code as product authority;
- managed brand metadata / stable `brandId`;
- managed Light/Dark Brand Logo publishing and Android cached rendering;
- vehicle switchers show managed Logo + nickname/fallback;
- range-standard metadata;
- JSON/CSV catalog import/export and template;
- managed Hero-key selection;
- filename-prefix batch Brand Logo / Hero upload;
- copyable Logo/Hero standards and prompt center;
- #259 Brand Logo batch-upload simplification and light-card contrast correction.

#244 is implementation-mostly-complete and now owns remaining maturity/physical acceptance. #20 owns provenance, normalization/conflict quality and broader real-model coverage, not a second catalog runtime/import architecture.

### Trip background/location reliability — #77

Current `main` includes:

- #80 removal of the old 8m callback displacement gate;
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

High-level split:

- `android-build.yml` — Android PR/push validation + Debug APK artifact;
- `android-release.yml` — manual production signed APK publish path;
- `hero-admin.yml` — resource-admin application/container/lifecycle validation;
- `hero-admin-deploy.yml` — production deployment of the resource admin on main push;
- `hero-assets-publish.yml` — Hero asset package validation;
- `admin-batch-image-upload.yml` — batch-image browser-contract validation;
- `admin-prompt-library.yml` — copy/prompt-library contract validation;
- `vehicle-catalog-admin-tools.yml` — catalog admin browser/import-export contract validation.

These workflows overlap by changed paths intentionally but do not have the same responsibility.

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

## Current governance queue

Completed in the 2026-08-31 audit:

- #6 authority graph reconciled;
- #203/#214/#222/#223/#224/#225 stale implementation Issues closed;
- #77/#235/#244/#20/#192/#205/#102/#94/#215 rewritten to current responsibilities;
- stale Draft PR #236/#255 closed without merge;
- README/LICENSE/templates/branch-stack rules merged by #263;
- #75 rewritten to exact current branch-protection/CI target;
- #265 created for stale-branch cleanup/merge-time deletion;
- #258/#261/#260/#251 descriptions synchronized to actual stacked state.

Remaining documentation debt:

- reconcile dated `ROADMAP.md` milestone narrative without turning it into an hourly status log;
- reconcile `PROJECT_MASTER.md` present-tense runtime wording where it conflicts with this file;
- reconcile older Trip/UI acceptance Issues that still call historical SHAs “latest main”;
- narrow old #70 local-Hero/model-whitelist wording to physical visual closeout;
- add explicit supersession/version notes to older docs that still look current.
