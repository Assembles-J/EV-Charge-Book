# EV Charge Book Current Status Authority

Updated: 2026-10-10
Status: Operational status authority
Baseline: `main@50e08fb7cf5895845f7e696f6ac6036960596f32` (latest GitHub head observed 2026-10-10; last commit 2026-09-14)

## Purpose

This file owns **fast-changing project execution status**. Stable product and architecture principles remain in `PROJECT_MASTER.md` and domain-specific authority documents.

When status sources disagree, use this order:

1. current `main` implementation facts and persisted schemas;
2. merged PR / current-head CI evidence;
3. this current-status authority;
4. owning Open Issue for remaining acceptance or future work;
5. older roadmap/history/versioned design text.

An Open Issue does not imply missing code. A Draft/unmerged PR is not runtime authority. CI Green is not physical acceptance. Historical Green CI does not authorize merge after the effective head/base changes.

## Executive status

The initial 2026-10-10 inspection found **53 Open Issues, 0 Open PRs** and `main@50e08fb`; the active follow-up work then opened PRs #347 (this authority reconciliation), #348 (tunnel watchdog recovery) and #349 (Android SDK CI setup). The latest-head Android Build and Android Release workflows completed successfully on 2026-09-14. This is CI evidence only, not physical or production-release acceptance.

Priority order:

1. latest-main physical Trip GPS/foreground/background/tunnel recovery acceptance (#77/#283), after merged #344;
2. latest-main widget swipe-stack/launcher acceptance (#301), after merged #343, and Bluetooth candidate/notification/auto-start acceptance (#235), after merged #346;
3. Charging v0.7 lifecycle, multi-vehicle unfinished-state, migration/reboot and billing/editor acceptance (#251/#321 and child owners);
4. Shanghai mainland basemap/provider feasibility (#199/#192), after merged #334/#345; current OpenFreeMap road/label usefulness remains unaccepted;
5. repository governance: `main` is still unprotected per 2026-10-10 branch metadata (#75), plus branch cleanup (#265) and documentation/Issue reconciliation (#6).

No new broad implementation stack should be inferred merely from historical unchecked checklists. Real-device acceptance and release-upgrade verification remain separate from Green CI.

## Current PR queue

### Current follow-up queue (opened 2026-10-10)

- **#347** — this docs-only status reconciliation; it must track the later October PR changes rather than claiming the initial zero-PR audit is still the current queue.
- **#348 — Draft Trip tunnel stale-callback recovery:** code proposal, **not runtime authority**. Fresh unique monotonic fix timestamps are required to refresh Fused/platform source watchdogs; stale/obsolete source callbacks are ignored, initial platform fallback registration is made exception-safe, and the same bounded 30s/60s retry budget is preserved. The one-line Android CI action v4 fix is included so exact-head tests run. Requires Green CI and physical road → tunnel → road evidence under #77/#283 before merge/closeout.
- **#349 — Android CI setup repair:** moves `android-actions/setup-android` from v3 to v4 because Google no longer serves the obsolete SDK `tools` package. The previous October CI failures were in SDK setup, **not** Kotlin compilation. Review/merge based on a fresh full Android CI result.

Historical drafts and pending queues below have been superseded by merged work; do not treat them as outstanding code-review items.

### Recently landed on current main

- **#344 — Trip GPS tunnel/provider stall hardening:** merged 2026-09-14; Fused/platform callbacks and watchdogs use dedicated restartable worker threads with `quitSafely()` disposal and scheduler-delay diagnostics. Physical tunnel and repeated-lifecycle acceptance remain under #77/#283. Earlier Draft #338 was closed unmerged and superseded.
- **#343 — vehicle home widget swipe stack:** merged 2026-09-14; native `StackView` swipe pages replace the previous tiny arrow controls. Re-test latest-main swipe, pinning, compact layout, process/reboot recovery and truthful local facts under #301; artifact #839 was a historical pre-#343 baseline.
- **#334 — OpenFreeMap Liberty diagnostics:** merged 2026-09-14; provider failures and fallback remain diagnosable. This was not a provider acceptance decision.
- **#345 — Trip route/map readability:** last merge to `main@50e08fb` on 2026-09-14; continuity-aware default framing, truthful long-gap rendering, minimal map controls and scroll gesture arbitration. Recorded Shanghai OpenFreeMap context remained effectively blank; provider choice is still #199's physical gate.
- **#346 — Bluetooth auto-Trip background fallback:** merged 2026-09-14; Android 12+ background restrictions degrade auto-start to a visible one-tap notification instead of silently creating a Trip. Foreground/background/notification-denied OEM acceptance remains #235.
- **#340 — Charging cross-month start-time attribution regression:** previously merged on current main and unchanged.

Latest main-head workflows on 2026-09-14: Android Build [run 34821831929](https://github.com/Assembles-J/EV-Charge-Book/actions/runs/34821831929) and Android Release [run 34822213280](https://github.com/Assembles-J/EV-Charge-Book/actions/runs/34822213280), both success. A successful Release workflow is not itself proof of an accepted signed in-place upgrade.

## Charging v0.7 — #251 / #321 closeout

Charging v0.7 is no longer an implementation-candidate stack. The core lifecycle/calculation architecture and focused September hardening are in `main`.

Merged hardening authority includes:

- #323 — same-vehicle Trip / Charging mutual exclusion in transactional authority;
- #325 — 30-day freshness guard for silent tariff auto-fill; stale/future facts are not silent authority;
- #327 — corrected/deleted linked completed records invalidate misleading reusable completed-session tariff memory;
- #340 — current-main cross-month start-time attribution regression, exact-head Android Build #842 Green.

The original #321 code blockers are resolved. #321 now owns closeout acceptance rather than another implementation stack.

Current product rules:

- no fake live SOC/BMS/charger-power telemetry;
- target SOC is intent, not an actual end-SOC fact;
- unknown meter/cost stays unknown, never fake zero;
- only user-confirmed meter/charger kWh may finalize a completed record;
- pending physical charge-end facts may update VehicleState but do not enter completed charging statistics;
- completion/backfill remains exactly-once under repository/session transaction authority;
- manual completed-record maintenance remains independent from active-session lifecycle;
- automatic tariff reuse requires matching vehicle/location/type/provenance and freshness;
- a cross-month completed Charging record is attributed by charging **start time**.

Remaining closeout:

- multi-vehicle unfinished-session discoverability;
- real previous-release -> current-release in-place upgrade;
- Room open/migration on real user data;
- ACTIVE/PENDING process-death and reboot recovery;
- complete/backfill retry without duplicate records;
- pending end-fact revision + VehicleState truth;
- cancel/delete produces no historical charging record;
- Dark/Light, 320–360dp, fontScale 1.3+, keyboard/decimal/back-navigation physical acceptance.

#252/#253/#260/#289 remain physical/semantic acceptance owners. #311 reusable presets remain future work and do not block v0.7 closeout.

## Trip reliability — #77

Current `main` contains the established Fused/platform, diagnostics and monotonic-timing baseline plus #319.

Important merged steps include:

- #231 — trusted-speed-first stationary distance handling;
- #275 — per-Trip diagnostic export;
- #278/#280 — OEM/background guidance and source/power observability;
- #281 — Room v17 TripPoint epoch + `elapsedRealtimeNanos`; monotonic timing is preferred for new Trip intervals/continuity;
- #319 — evidence-driven bounded platform-provider re-registration and contradictory zero-speed displacement protection.

#319 was driven by real Trip 32 evidence. It intentionally does **not** add a second tracking Service, WorkManager/Alarm heartbeat, long WakeLock, battery exemption, synthetic route points or unlimited provider retries.

Trip 34 then produced a scheduler/provider-stall hypothesis. Original Draft #338 had a worker-thread lifecycle leak risk and was closed unmerged. The corrected implementation **#344 has now merged**: Fused/platform callbacks and silence watchdogs are moved off the main looper; owned threads are restartable and disposed via `quitSafely()`; watchdog diagnostic timing is recorded. This is a merged code fix, **not** physical tunnel/lock-screen/provider recovery acceptance. Verify latest-main start → stop → start thread ownership and the road → tunnel → road diagnostic CSV before closing #77.

October 10 focused Draft #348 additionally addresses stale/duplicate capture timestamps resetting Fused/platform watchdogs, late callbacks crossing session boundaries, and initial platform registration exception handling. These changes are **not in main** pending CI/physical acceptance; the recovery budget and persisted Trip truth boundaries remain unchanged.

Locked truth boundaries:

- `capturedAtElapsedRealtimeNanos` is preferred for new Trip point ordering/interval/long-gap decisions when available;
- civil epoch remains a readable/exportable fact and historical fallback;
- `LONG_GAP_SECONDS = 120` remains a hard continuity boundary;
- no synthetic points and no fabricated route/distance/speed across true gap/rebase boundaries;
- stationary GNSS drift must not become distance;
- provider recovery changes acquisition/diagnostics, not persisted fact meaning.

Remaining P0 evidence is a **latest-main real-device drive** covering lock screen, another app foreground, stationary hold, provider interruption/recovery and final Trip completion. If #338 advances, its tunnel test becomes an additional focused acceptance rather than a replacement for #77's broader matrix.

### #283 OEM/recovery follow-up

The old question “should bounded platform recovery be implemented?” is partially answered by merged #319. #283 now owns the OEM compatibility matrix and any **evidence-driven** next recovery step. #344 is the merged Trip 34 recovery implementation; its physical OEM/tunnel acceptance and any further recovery changes remain evidence-gated.

### #137 distance-trust investigation

The August 9.x-km symptom remains historical evidence, not a confirmed current algorithm bug. If latest-main reproduces material distance inflation, diagnose from persisted TripPoints/diagnostics and odometer/reference-route evidence before changing distance trust rules.

### #215 elapsed timer/finalization

Current-main audit confirms `ChargingRepository.stopActiveTrip()` writes final `elapsedSeconds` from:

`TripRules.elapsedSeconds(active.startedAtEpochMillis, endedAtEpochMillis)`

Therefore final duration does not depend on the last GPS callback. #215 no longer has a production-code finalization gap; it remains open only for latest-main physical elapsed/finalization consistency.

## Android home widget — #301

Stage-1 Local First implementation is in `main`.

Merged progression includes:

- #306 — Local First widget baseline;
- #330 — presentation redesign / compact-expanded behavior;
- #332 — provider exposure to launcher;
- #336 — direct standard-Android pin flow from `车辆 → 连接与数据 → 桌面小组件` using `AppWidgetManager.requestPinAppWidget()`;
- #339 — three deterministic pages (vehicle / Trip / Charging) and earlier arrow navigation, plus ColorOS data-area collapse fix;
- #343 — native swipeable `StackView` pages replacing the earlier arrows, tightened widget bounds and page-tap opening.

#339 was merged as `4bd7459f770f404778ce8ea1c45da02fdb974aa0`; its historical main-push Build #839 was Green. **#343 supersedes its arrow-page interaction** and is already merged; the #839 artifact is no longer the latest-main acceptance target.

Truth contract:

- show only app-known Local First facts;
- unknown SOC/mileage stays `--` / `-- km`;
- do not infer live connection/range/lock/location/OEM-control state;
- Trip/Charging actions must reuse existing business authority;
- telemetry-aware presentation remains future work under #300.

Remaining #301 gate: install **latest-main APK** (not the old #839 artifact), remove/re-add the widget if launcher metadata changed, then verify direct pin, **swipe-based** vehicle → Trip → Charging pages (rather than obsolete arrow controls), page-tap open, no clipping/collapse, truthful unknowns, process death/reboot, state refresh, vehicle switch, active Trip/Charging actions, Dark/Light and at least one non-OPPO launcher.

ColorOS OEM `卡片中心` is **not** the standard AppWidget authority. Native ColorOS card feasibility is separate under #337 and must use official OEM APIs/SDKs only.

## Trip map/provider — #192 / #199 / #334 / #345

The MapLibre + replaceable style/provider baseline and truth-preserving no-basemap fallback are in `main`. #334 merged OpenFreeMap `liberty`/diagnostic improvements; #345 merged continuity-aware default route framing, explicit `回到全程`, LONG_GAP unknown rendering and gesture simplification.

**The Shanghai provider decision is still open.** Real-device feedback recorded in #345 describes an effectively blank OpenFreeMap background with no useful local roads/labels. Confirm whether this is missing tile delivery or inadequate road/label coverage using on-device diagnostics before selecting a mainland provider adapter. Keep WGS84 persisted points untouched, keep renderer-only transforms at provider boundaries, retain attribution, and preserve truthful no-basemap fallback. #199 is the decision/acceptance owner; these merges do not close it.

## Vehicle/catalog/resource workflow — #244 / #20

The unified managed-resource workflow remains the current admin authority. #244 owns real workflow/device maturity; #20 owns catalog data quality, provenance, normalization, conflict correction, model coverage and coverage metrics.

Do not build a second disconnected Logo/Hero/catalog onboarding system from historical Issues.

## Bluetooth-triggered Trip — #235

The persisted per-vehicle Bluetooth detection/auto-start foundation is in `main`. Merged #346 now adds Android 12+ background-restricted **visible one-tap notification fallback** and visible foreground auto-start feedback without treating Bluetooth presence as proof of driving. #235 remains for actual OEM/background/lock-screen/notification-denied physical acceptance, reconnect de-duplication, trigger-quality policy and possible later verified movement. Bluetooth connection alone is not proven driving evidence.

## Updater — #102

Runtime includes release discovery, DownloadManager, SHA-256, installer handoff, prompt dedupe and persisted recovery of download/install-ready state across process restart. #102 remains only as the production old-APK -> new-APK physical in-place-upgrade acceptance owner.

## Repository governance

PR #263 established the stable README, MIT license, PR template, evidence-first Issue templates and `BRANCH_AND_PR_GOVERNANCE.md`.

Remaining repository-setting owners:

- #75 — protect `main` and require current-head Android CI; **branch API still reports `main.protected=false` on 2026-10-10**;
- #265 — stale remote branch cleanup and merge-time branch deletion.

Repository metadata still needs to be the source of truth before either Issue closes. Do not claim a setting is complete from documentation alone.

`android-build.yml` intentionally triggers only for `android/**` or changes to that workflow itself. A docs-only PR therefore does not produce an Android Build and should not manufacture a runtime change merely to trigger CI.

The exact GitHub Actions inventory should be maintained in `WORKFLOW_OWNERSHIP.md`; this status file intentionally no longer freezes the old “nine workflow files” count because admin/helper workflows have continued to evolve.

## Documentation governance — #6

2026-09-14 reconciliation (historical baseline):

- #215 rewritten from “partial implementation” to code-complete / physical-only finalization evidence;
- #321 rewritten from stale code blockers to Charging v0.7 closeout;
- #301 synchronized through merged #339 and widget artifact #839;
- #77 synchronized through merged #319;
- #283 reframed around OEM matrix / evidence-driven follow-up after #319;
- #137 reframed as latest-main data-trust investigation;
- stale PR #328 closed; current-main replacement #340 passed exact-head Build #842 and merged;
- final open-PR audit found Draft #338; its Trip 34 evidence and HandlerThread lifecycle blocker are now explicitly recorded rather than omitted;
- this authority baseline moved from 2026-09-02 to 2026-09-14.

2026-10-10 follow-up: reconciled merged #343/#344/#345/#346/#334 and closed unmerged #338; the initial zero-PR audit was followed by active PRs #347/#348/#349. The main branch remains unprotected in the last verified metadata. Owning Issues #77/#301/#199/#235 should use current-main acceptance criteria rather than historical artifact/arrow/Draft wording.

Remaining documentation debt:

- normalize old Trip/UI acceptance Issues that still call historical SHAs “latest main”;
- narrow old #70 local-Hero/model-whitelist wording to physical visual closeout;
- add explicit supersession/version notes to older docs that still look current;
- periodically re-audit `WORKFLOW_OWNERSHIP.md` as admin helper surfaces evolve.

## Authority maintenance rules

1. Update the owning Issue whenever implementation stage changes.
2. Close superseded implementation-only Issues when their code is merged and physical acceptance is owned elsewhere.
3. Keep physical-only Issues explicitly written as physical acceptance; do not leave stale implementation checklists.
4. Draft/unmerged PRs are not runtime authority.
5. `PROJECT_MASTER.md` owns stable architecture/product principles; this file owns fast-moving execution status.
6. `ROADMAP.md` owns milestone ordering, not current merge/CI facts.
7. Historical design/reference docs remain useful only within explicit version/supersession boundaries.
8. No broad reimplementation may start from an old Issue before checking current `main` and merged PR history.
9. Historical Green CI does not authorize merge after effective head/base changes.
10. CI Green and physical/production acceptance remain distinct evidence classes.
