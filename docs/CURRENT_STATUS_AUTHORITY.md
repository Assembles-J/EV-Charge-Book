# EV Charge Book Current Status Authority

Updated: 2026-08-31
Status: Operational status authority
Baseline: `main@2ca393490fcd027e016261cf09c383cd70387de8`

## Purpose

This file owns fast-changing project execution status. Stable product and architecture principles remain in `PROJECT_MASTER.md` and the domain-specific authority documents.

When status sources disagree, use this order:

1. current `main` implementation facts and persisted schemas;
2. merged PR / CI evidence;
3. this current-status authority;
4. owning open Issue for remaining acceptance or future work;
5. older roadmap/history text.

An open Issue does not imply missing code. A draft/unmerged PR is not runtime authority.

## Current repository governance

- `main` is currently not branch-protected and has no required status checks. #75 remains a valid repository-governance issue.
- Code, CI, physical functional acceptance, physical visual acceptance and Production Release acceptance are distinct states.
- Physical acceptance must never be inferred from CI Green.
- Implementation issues that are fully merged should not remain written as future implementation work. If physical acceptance is still needed, move that responsibility to an explicit acceptance owner or rewrite the Issue as physical-only.

## Active delivery streams

### Charging v0.7 — parent #251

Current product authority:

- #251 parent workflow and data-truth rules
- `CHARGING_V0.7_DESIGN_AND_IMPLEMENTATION_PLAN.md`
- #252 coupled-field calculation / derived metrics
- #253 optional active charging session / preset lifecycle
- #254 current-location default / future map-point picker

Current implementation stage:

- PR #258 is a Draft first code slice for #252.
- #258 establishes the pure calculation-engine contract only; it does not yet wire Add/Edit UI state or active-session persistence.
- #252 therefore remains open after that domain slice until the editing state adopts the centralized rules and its acceptance examples are satisfied.
- #253 and #254 remain valid future implementation owners.

### Vehicle maturity / catalog — #244 and #20

Already in `main`:

- user vehicle nickname and nickname-first display;
- managed-catalog-only primary add flow;
- standard vehicle facts read-only in Android;
- removal of Android supported-model hard-code as the product authority;
- managed brand metadata / stable `brandId`;
- managed light/dark Brand Logo publishing and Android cached rendering;
- vehicle switchers show managed Logo + nickname/fallback;
- range-standard metadata;
- JSON/CSV catalog import/export and template;
- managed Hero-key selection;
- filename-prefix batch Brand Logo / Hero upload;
- copyable Logo/Hero standards and prompt center;
- PR #259 simplified Brand Logo batch upload and restored light-card contrast.

#244 must be treated as implementation-mostly-complete and should track only remaining acceptance/data gaps, not repeat unchecked implementation tasks.

#20 remains valid for catalog provenance, broader real-model coverage, normalization/conflict quality and coverage metrics. Its old “build CSV/JSON bulk import” item is now implemented and must not remain an active task.

### Trip background/location reliability — #77

Current `main` is materially newer than the old post-#184 baseline. Implemented after that baseline includes:

- Google Play Fused production source (#217);
- non-GMS/platform GPS + network fallback (#220);
- ~1 Hz active Trip location target and ~2 s stationary heartbeat (#226);
- trusted-speed-first stationary distance handling (#231);
- silent-provider recovery: platform no longer trusts framework `fused`, and Google Fused falls back after a 12 s no-callback watchdog (#234).

Truth boundaries remain:

- original `Location.time` is authoritative;
- no synthetic route points;
- `LONG_GAP_SECONDS = 120` remains a hard continuity boundary;
- stationary GNSS drift must not become distance.

#77 remains open for current-main physical lock-screen/background/provider-loss acceptance. #203 no longer owns a missing Fused migration; that implementation is complete and its remaining physical responsibility belongs in #77.

### Bluetooth-triggered Trip — #235

Current `main` has advanced beyond the original exploration-only baseline:

- #239: auto-trip mode/state/reason model + pure eligibility policy;
- #241: persisted per-vehicle Bluetooth detection sessions, dedupe, ignore handling and notification entry flow;
- #242: explicit per-vehicle `蓝牙连接后自动开始行程` option routed through `TripStartCoordinator`, with location/notification/active-Trip guards.

Therefore any document or Issue that still says auto-start is wholly unimplemented is stale.

#235 remains open for real-device/OEM/background acceptance, trigger-quality policy and later verified-movement / parking-assistant work. Bluetooth connection alone must not be documented as proven driving evidence.

### Trip interaction / analysis follow-ups

Already implemented in `main`:

- interactive route pan/pinch/reset and trusted-speed route encoding (#219/#221/#230);
- interactive trend pan/zoom/tap and stock-style long-press drag inspection (#218/#227);
- smoother SOC wheel gesture behavior (#230);
- uncertainty-aware altitude filtering for completed and active trends (#228/#232);
- recent-consumption-based Trip end-SOC estimator seed (#213).

Remaining map work under #192/#199 is specifically basemap/provider/context validation, road labels, licensing/attribution, China-region behavior and truthful fallback — not basic pan/zoom.

### Updater

Updater runtime has continued beyond the early #102 description, including non-blocking DownloadManager behavior, prompt dedupe and persisted recovery of an install-ready download after process restart (#247).

#102 remains valid only as the physical production old-APK -> new-APK in-place upgrade acceptance owner. It must not be read as evidence that updater root wiring is still missing.

## Authority maintenance rules

1. Update the owning Issue whenever implementation stage changes.
2. Close superseded implementation-only Issues when their code is merged and physical acceptance is owned elsewhere.
3. Keep physical-only Issues explicitly labeled in their body as physical acceptance; do not leave unchecked implementation lists.
4. Draft PRs may propose design, but they are not project authority until merged or explicitly adopted by an owning Issue.
5. `PROJECT_MASTER.md` owns stable architecture/product principles; this file owns fast-moving execution status.
6. `ROADMAP.md` should describe ordering and milestones, but when its dated status lags this file, this file wins for current execution state.
7. Historical design/reference documents remain useful only when their supersession boundary is explicit.
8. No broad reimplementation may be started from an old Issue without first checking current `main` and merged PR history.

## Immediate governance queue

- reconcile #6 to this status authority and the 2026-08-31 baseline;
- reconcile #77 and close/supersede #203 as a missing-implementation owner;
- reconcile #235 and remove stale pre-implementation wording;
- reconcile #244 and #20 with the merged catalog/Logo/batch-maintenance baseline;
- reconcile #205 to mark recent historical-consumption estimator input implemented by #213;
- reconcile #192 so basic route pan/zoom is marked implemented while provider/basemap remains open;
- close or rewrite #222/#223/#224/#225 so merged implementation is not represented as future work;
- keep #75 open while `main` remains unprotected.
