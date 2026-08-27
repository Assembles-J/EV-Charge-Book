# EV Charge Book v0.5 UI Implementation Plan

更新时间: 2026-08-27
状态: Implementation merged; device polish/hardening remains

## Goal

Implement the approved Dark First design language while keeping v0.5 focused on existing product capabilities and trustworthy data.

## Phase 1 - Theme Foundation — DONE

- [x] EV color tokens
- [x] unified dark surfaces
- [x] Dark First default
- [x] explicit persisted Light mode
- [x] standardized spacing, typography and shapes
- [x] reusable Compose primitives/empty states
- [x] flattened bottom navigation selected state

## Phase 2 - Dashboard — DONE

Dashboard is vehicle-focused rather than a generic metric-card collection:

- [x] Hero Vehicle Card
- [x] exact local drawable vehicle artwork mapping with fallback
- [x] stored battery capacity and rated range only
- [x] monthly energy/cost cockpit
- [x] recent charging timeline
- [x] no fabricated live SOC/remaining range

## Phase 3 - Charging Experience — DONE

- [x] charging journal cockpit
- [x] timeline history
- [x] SOC transition
- [x] cost/energy emphasis
- [x] unit price and optional metadata
- [x] Add/Edit charge forms aligned to the same hierarchy

## Phase 4 - Trip Experience — DONE FOR VISUAL BASELINE

- [x] READY / LIVE / INTERRUPTED cockpit states
- [x] recent/history trip presentation
- [x] trip detail summary
- [x] true route geometry rendering
- [x] GPS gap / credibility presentation
- [x] diagnostics aligned to the cockpit language

Trip reliability remains governed by the dedicated Trip reliability issues/docs. UI completion does not mean long-drive GPS continuity is physically accepted.

## Phase 5 - Analytics and Vehicle — DONE

- [x] monthly energy cockpit
- [x] month comparison
- [x] charger mix and common places
- [x] lifetime totals
- [x] interval estimates and Trip coverage evidence
- [x] vehicle garage and switching
- [x] vehicle catalog/editor
- [x] Bluetooth prompt and backup/CSV utility rows
- [x] persisted Dark / Light appearance control

## Vehicle Artwork — DONE FOR CURRENT COVERAGE

Runtime Base64/network image loading has been removed.

Compiled resources:

- `drawable-nodpi/byd_seal_2025.webp`
- `drawable-nodpi/leapmotor_c16_2026.webp`
- `drawable-nodpi/xiaomi_su7_2024.webp`
- `drawable-nodpi/tesla_model_3.webp`

Coverage is intentionally strict and incomplete. Unsupported models use a fallback instead of a wrong vehicle image. Broader catalog/artwork coverage belongs to the catalog pipeline rather than ad-hoc UI hardcoding.

## Automated Acceptance

- [x] PR #71 merged into `main`
- [x] latest cumulative branch baseline included current `main`
- [x] Android Build Run #294 Green
- [x] Debug APK artifact generated
- [x] strict vehicle artwork mapping tests

## Remaining Acceptance / Hardening

These are follow-up acceptance items, not reasons to reopen the completed visual implementation phases:

- [ ] five-primary-page real-device visual pass
- [ ] Light mode contrast pass
- [ ] 320-360dp / fontScale 1.3-1.5 pass
- [ ] long text truncation/overflow pass
- [ ] TalkBack/contentDescription pass
- [ ] dirty-form back protection
- [ ] active Trip data-management guards
- [ ] keyboard/IME long-form usability
- [ ] final small animation/micro-interaction polish where it materially helps

Primary owner for usability/state-safety hardening: Issue #42.

Location/address fallback and physical location acceptance remain tracked separately by #66 and #14.

## Development Rule

Do not expand visual polish into unrelated v0.5 business scope. Prefer small maintainable fixes backed by real-device findings.
