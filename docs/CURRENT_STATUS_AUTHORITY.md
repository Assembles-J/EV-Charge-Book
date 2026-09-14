# EV Charge Book Current Status Authority

Updated: 2026-09-14
Status: Operational status authority
Baseline: `main@aa7ec97ba76f3c408fe0122a82587f23c1a5aaf1`

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

The repository is no longer in a broad implementation phase. The current priority is:

1. current-main physical acceptance for Trip reliability and the standard Android home widget;
2. finish Charging v0.7 physical closeout;
3. resolve the focused #338 Trip tunnel/provider-stall Draft safely, then validate it on device;
4. validate the mainland-China Trip basemap/provider decision;
5. normalize old physical-only Issues and finish repository governance settings.

Do not start a new implementation stack from an old Issue before checking current `main` and merged PR history.

## Current PR queue

### #338 — Trip tunnel/provider-stall recovery hardening

- Draft, not runtime authority;
- based on Trip 34 evidence where a moving gap recovered much later than the intended watchdog window;
- proposed change moves Fused/platform callbacks and silence watchdogs away from the main looper and adds watchdog timing diagnostics (`expectedDelayMs`, `actualDelayMs`, `lateByMs`);
- historical Android Build #836 is Green on head `56ad8491c21f7d379067ba6052c85313f208e4e9`, but that does not authorize merge onto the later current main;
- current review blocker: both proposed `HandlerThread`s start during source construction while `stop()` removes updates/callbacks without quitting the owned threads. Because `TripTrackingService` can create a new `FusedTripLocationSource` on later registrations/lifecycles, the Draft needs explicit thread ownership/disposal before normalization;
- after lifecycle/resource ownership is fixed with focused evidence, replay/sync onto current main, obtain fresh exact-head Android CI, then run the stated real-device normal-road -> Hongmei South Road tunnel -> open-road acceptance;
- preserve existing 12s Fused failover, bounded platform re-registration, no synthetic tunnel points and no fabricated distance while location is unavailable.

### #334 — Trip basemap diagnosability / OpenFreeMap Liberty trial

- Draft physical experiment, not runtime authority;
- changes OpenFreeMap style to `liberty` and adds debug-only MapLibre basemap diagnostics;
- historical Android Build #831 was Green on its old head;
- must be validated on the target Shanghai device before provider choice;
- useful tiles + roads/labels -> OpenFreeMap remains viable for display;
- loaded tiles but unusable mainland roads/labels, or persistent delivery errors -> reject OpenFreeMap for this product and move #199 toward an official mainland provider such as AMap;
- if #334 is later merged, first normalize it onto current `main` and obtain new current-head CI.

### Recently merged: #340 Charging monthly attribution regression

#328 was closed as stale/superseded. The same focused one-file regression was replayed from current main as #340. Android Build #842 passed on exact head `399e9f3b3e352250712fa7791deafca00602b6ff`, then #340 was squash-merged as `aa7ec97ba76f3c408fe0122a82587f23c1a5aaf1`.

The locked product rule is unchanged: a Charging event spanning midnight/month boundaries belongs to the date/month where it **started**, using `ChargingRecordEntity.chargeTimeEpochMillis`.

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

Trip 34 later produced a separate scheduler/provider-stall hypothesis tracked by Draft #338. Until #338's thread lifecycle blocker is resolved, normalized onto current main and physically accepted, it is not runtime authority and must not be described as a completed reliability fix.

Locked truth boundaries:

- `capturedAtElapsedRealtimeNanos` is preferred for new Trip point ordering/interval/long-gap decisions when available;
- civil epoch remains a readable/exportable fact and historical fallback;
- `LONG_GAP_SECONDS = 120` remains a hard continuity boundary;
- no synthetic points and no fabricated route/distance/speed across true gap/rebase boundaries;
- stationary GNSS drift must not become distance;
- provider recovery changes acquisition/diagnostics, not persisted fact meaning.

Remaining P0 evidence is a **latest-main real-device drive** covering lock screen, another app foreground, stationary hold, provider interruption/recovery and final Trip completion. If #338 advances, its tunnel test becomes an additional focused acceptance rather than a replacement for #77's broader matrix.

### #283 OEM/recovery follow-up

The old question “should bounded platform recovery be implemented?” is partially answered by merged #319. #283 now owns the OEM compatibility matrix and any **evidence-driven** next recovery step. Draft #338 is such a focused candidate from Trip 34 evidence, but its unmerged code is not current authority.

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
- #339 — three deterministic pages (vehicle / Trip / Charging), per-widget previous/next navigation and `1/3` indicator, plus ColorOS data-area collapse fix.

#339 was merged as `4bd7459f770f404778ce8ea1c45da02fdb974aa0`. PR-head Android Build #838 and main-push Build #839 were Green; #839 produced the current widget-acceptance Debug APK.

Truth contract:

- show only app-known Local First facts;
- unknown SOC/mileage stays `--` / `-- km`;
- do not infer live connection/range/lock/location/OEM-control state;
- Trip/Charging actions must reuse existing business authority;
- telemetry-aware presentation remains future work under #300.

Remaining #301 gate: install current-main APK, remove/re-add the widget so launcher metadata is reapplied, then verify direct pin, 1/3 -> 2/3 -> 3/3 navigation, no clipping/collapse, truthful unknowns, process death/reboot, state refresh, vehicle switch, active Trip/Charging actions, Dark/Light and at least one non-OPPO launcher.

ColorOS OEM `卡片中心` is **not** the standard AppWidget authority. Native ColorOS card feasibility is separate under #337 and must use official OEM APIs/SDKs only.

## Trip map/provider — #192 / #199 / #334

Basic route interaction is already implemented. Remaining work is provider/context validation for mainland China: road/label usefulness, delivery reliability, licensing/attribution and truthful fallback.

Do not merge a provider implementation merely because historical CI is Green. #334 is a physical provider experiment and its Shanghai result decides whether OpenFreeMap remains viable or #199 moves to an official mainland provider adapter.

Persistent Trip truth remains WGS84. If a mainland renderer requires GCJ-02, conversion belongs at the renderer/provider adapter boundary rather than rewriting persisted Trip facts.

## Vehicle/catalog/resource workflow — #244 / #20

The unified managed-resource workflow remains the current admin authority. #244 owns real workflow/device maturity; #20 owns catalog data quality, provenance, normalization, conflict correction, model coverage and coverage metrics.

Do not build a second disconnected Logo/Hero/catalog onboarding system from historical Issues.

## Bluetooth-triggered Trip — #235

The persisted per-vehicle Bluetooth detection/auto-start foundation is already in `main`. #235 remains for OEM/background physical acceptance, trigger-quality policy and possible later verified-movement evolution. Bluetooth connection alone is not proven driving evidence.

## Updater — #102

Runtime includes release discovery, DownloadManager, SHA-256, installer handoff, prompt dedupe and persisted recovery of download/install-ready state across process restart. #102 remains only as the production old-APK -> new-APK physical in-place-upgrade acceptance owner.

## Repository governance

PR #263 established the stable README, MIT license, PR template, evidence-first Issue templates and `BRANCH_AND_PR_GOVERNANCE.md`.

Remaining repository-setting owners:

- #75 — protect `main` and require current-head Android CI;
- #265 — stale remote branch cleanup and merge-time branch deletion.

Repository metadata still needs to be the source of truth before either Issue closes. Do not claim a setting is complete from documentation alone.

`android-build.yml` intentionally triggers only for `android/**` or changes to that workflow itself. A docs-only PR therefore does not produce an Android Build and should not manufacture a runtime change merely to trigger CI.

The exact GitHub Actions inventory should be maintained in `WORKFLOW_OWNERSHIP.md`; this status file intentionally no longer freezes the old “nine workflow files” count because admin/helper workflows have continued to evolve.

## Documentation governance — #6

2026-09-14 reconciliation:

- #215 rewritten from “partial implementation” to code-complete / physical-only finalization evidence;
- #321 rewritten from stale code blockers to Charging v0.7 closeout;
- #301 synchronized through merged #339 and widget artifact #839;
- #77 synchronized through merged #319;
- #283 reframed around OEM matrix / evidence-driven follow-up after #319;
- #137 reframed as latest-main data-trust investigation;
- stale PR #328 closed; current-main replacement #340 passed exact-head Build #842 and merged;
- final open-PR audit found Draft #338; its Trip 34 evidence and HandlerThread lifecycle blocker are now explicitly recorded rather than omitted;
- this authority baseline moved from 2026-09-02 to 2026-09-14.

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
