# Bluetooth Auto-Trip Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make configured vehicle Bluetooth connections reliably either start a Trip automatically when Android permits it, or degrade to a visible one-tap start notification instead of silently failing.

**Architecture:** Keep `VehiclePresenceDispatcher` and `TripStartCoordinator` as the existing authorities. Add a small pure policy that decides direct auto-start vs user-action fallback based on Android version and whether the app is visibly foreground, then let `AutoTripPromptCoordinator` select the existing direct path or a notification path. Keep the persisted detection session as `BLUETOOTH_CANDIDATE` until the user-action fallback is tapped, so no phantom/interrupted Trip is created merely because a background FGS start is disallowed.

**Tech Stack:** Kotlin, Android BroadcastReceiver/notifications, Room, existing `TripTrackingService`, JUnit 4.

**Spec:** GitHub Issue #235 and current main (`bcbc6a44dcfdd0861595d116bfa4d051ef220284`).

## Global Constraints

- Bluetooth connection is a candidate/automation trigger, not proof of physical driving.
- Do not start a second Trip when an active Trip already exists.
- Do not silently end a Trip on Bluetooth disconnect.
- Android 13+ notification denial must not produce invisible automatic recording.
- Android 12+ background execution restrictions must degrade to a visible user-action path rather than create a failed/interrupted Trip.
- Keep all Trip creation routed through `TripStartCoordinator`.
- No new background service, WorkManager loop, wake lock, or telemetry claim.

---

### Task 1: Pure auto-start execution policy

**Files:**
- Modify: `android/app/src/main/java/com/evchargebook/autotrip/AutoTripPolicy.kt`
- Test: `android/app/src/test/java/com/evchargebook/autotrip/AutoTripEligibilityPolicyTest.kt`

**Interfaces:**
- Produces: `BluetoothAutoStartExecutionPolicy.decide(autoStartEnabled, sdkInt, appInForeground)`.

- [ ] Write failing tests covering prompt-only, pre-Android-12 background direct start, Android-12+ foreground direct start, and Android-12+ background user-action fallback.
- [ ] Verify the new tests fail because the execution policy does not yet exist.
- [ ] Add the smallest pure policy implementation.
- [ ] Verify the policy tests pass.

### Task 2: Reliable visible fallback and success feedback

**Files:**
- Modify: `android/app/src/main/java/com/evchargebook/autotrip/AutoTripPromptCoordinator.kt`
- Modify: `android/app/src/main/java/com/evchargebook/ui/vehicle/BluetoothPromptScreen.kt`
- Test: `android/app/src/test/java/com/evchargebook/autotrip/AutoTripPromptCoordinatorTest.kt`

**Interfaces:**
- Consumes: `BluetoothAutoStartExecutionPolicy`.
- Produces: direct auto-start while foreground/legacy-compatible; one-tap notification fallback while Android 12+ background; explicit auto-start success notification copy.

- [ ] Add failing tests for truthful notification copy/decision labels used by the controller.
- [ ] Verify the new tests fail.
- [ ] Add a conservative foreground-process check and route Android 12+ background auto-start to a one-tap notification that reuses `AutoTripConfirmationActivity`.
- [ ] Add an explicit short-lived “行程已自动开始” notification after a successful direct Bluetooth auto-start.
- [ ] Update Bluetooth settings copy to explain the fallback instead of promising unconditional background auto-start.
- [ ] Verify tests pass.

### Task 3: Verification and repository handoff

**Files:**
- No production files beyond Tasks 1–2.

- [ ] Run the focused pure Kotlin/unit-test verification available locally.
- [ ] Open a PR against `main` with #235 as owning Issue and physical acceptance explicitly remaining open.
- [ ] Confirm GitHub Android Build on the PR head.
- [ ] Do not claim physical reliability until a current-main real-device test covers foreground, killed/background, lock screen, duplicate reconnect, and notification-tap fallback.
