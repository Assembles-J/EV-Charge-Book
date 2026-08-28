# EV Charge Book Dashboard — Current vs Reference Visual Gap Checklist

Date: 2026-08-28  
Status: **Active visual convergence checklist**  
Scope: Android Dashboard / `总览`  
Reference: approved 1:1 target screenshot documented in `DASHBOARD_REFERENCE_1TO1_REPLICATION_SPEC_V0.5.md`  
Current implementation snapshot: user-supplied screenshot after the latest BYD Seal Hero update

> This document is the delta list between the latest running UI screenshot and the approved reference. It does not replace the 1:1 replication specification. The reference screenshot remains the visual source of truth.

## 1. Current assessment

The current implementation is now directionally close enough that the next work should be **visual convergence**, not another redesign.

What is already working:

- dark-first visual direction is correct;
- BYD Seal Hero artwork quality and vehicle rendering are usable;
- `MY EV` + brand + model identity hierarchy is recognizable;
- glass-style Hero state panel direction is correct;
- recent-trip and energy cards use the correct dark/green visual family;
- five-item bottom navigation has the correct semantic order;
- selected navigation state is already green and unselected states are muted;
- the page no longer looks like a generic Material dashboard.

The remaining mismatch is mainly **geometry, composition, information completeness, and viewport fit**.

## 2. Priority summary

| Priority | Gap | Current state | Target state | Required action |
|---|---|---|---|---|
| P0 | Hero composition | identity block and artwork appear as two vertically separated regions | one continuous edge-to-edge Hero artwork with text overlaid | remove the visible hard split / opaque header block |
| P0 | Hero total height | substantially taller than target and pushes lower cards below the fold | Hero occupies roughly the same proportion as reference | reduce Hero height and retune artwork crop |
| P0 | Hero state panel | only two logical columns visible in empty state | fixed three-column SOC / mileage / latest-trip geometry | preserve three columns even when values are unavailable |
| P0 | Energy card viewport fit | only header is visible before bottom nav | full header + three metrics + progress line visible | reclaim vertical space above navigation |
| P0 | Dashboard outer gutter | current cards are visibly narrower than reference | narrow ~8dp-equivalent side gutter | reduce page horizontal padding |
| P1 | Vehicle switch control | missing from current Hero | circular car + chevron control at Hero top-right | restore control and align to reference |
| P1 | Recent Trip content | empty-state card is short and structurally different | target header/route/metrics composition when data exists | implement reference structure and truthful empty state |
| P1 | Card-to-card gaps | larger than reference | compact 6–8dp visual rhythm | reduce vertical spacing |
| P1 | Energy header action | shows `0 次` only | `0 次充电` + muted chevron | match exact content hierarchy |
| P1 | Bottom navigation container | close, but still reads flatter than reference | dock-like dark surface with rounded top geometry | tune container shape/background/safe area |
| P2 | Typography micro-tuning | generally close | screenshot-matched line height/weight/spacing | tune only after P0/P1 geometry is stable |
| P2 | Green / divider intensity | generally close | subtle reference-matched contrast | tune by overlay, not by eye alone |

## 3. P0 — Hero must become one continuous composition

### Current screenshot

The Hero currently reads as:

```text
[ dark green identity block ]
[ hard horizontal boundary ]
[ aurora + vehicle artwork ]
[ glass state panel ]
```

The horizontal boundary between the identity area and the artwork is visually obvious. This makes the top section feel like a separate header placed above an image.

### Reference

The reference reads as **one continuous Hero surface**:

```text
[ finished aurora/vehicle artwork fills entire Hero ]
        + MY EV / brand / model overlaid at top-left
        + vehicle switch overlaid at top-right
        + state glass panel overlaid near bottom
```

There must not be a solid opaque header region that cuts the artwork in half.

### Required implementation behavior

- the vehicle artwork must fill the complete clipped Hero bounds;
- `MY EV`, brand and model are overlay content;
- the top area may use a very subtle readability scrim only if required;
- any scrim must be a soft gradient/alpha treatment with no visible horizontal seam;
- do not render a separate green rectangular header background;
- preserve the current finished artwork; do not recreate aurora/reflection in Compose.

**Acceptance:** at normal viewing distance, the user should perceive one Hero image/card, not `header + image`.

## 4. P0 — Hero height is currently too large

The current Hero consumes much more vertical space than the approved reference. This is the main reason the Monthly Energy metrics are pushed below the visible viewport.

The reference Hero is approximately `751 / 1672 ≈ 44.9%` of the full screenshot height.

The latest implementation visually exceeds this proportion by a large margin because it stacks a dedicated identity region above a large artwork region.

### Required change

After converting the identity block to overlay content:

- retune the Hero container height toward the approved reference proportion;
- crop the artwork rather than increasing Hero height to preserve every pixel;
- preserve the car body, wheels and lower reflection as the crop priority;
- position the car lower-middle, but avoid leaving a very large unused aurora-only area above it;
- keep the state panel inside the Hero, not as extra height below the image.

### Do not solve this by

- shrinking all text globally;
- shrinking the bottom navigation;
- hiding energy metrics;
- making the page scroll position the only way to see the target content.

The first viewport should match the reference composition.

## 5. P0 — Horizontal gutter is too wide

The latest screenshot uses noticeably larger left/right card margins than the reference.

Reference baseline:

- Hero/card outer gutter is approximately 20px on a 941px-wide screenshot;
- normalized target is approximately 8dp on a 360dp content width.

### Required change

For the Dashboard only:

- target an approximately 8dp outer horizontal gutter;
- do not blindly reuse a generic 16dp page padding token if it makes the cards too narrow;
- Hero, Recent Trip and Monthly Energy should align to the same outer x positions.

This is a Dashboard-specific visual requirement and may intentionally differ from other screens.

## 6. P0 — Hero glass state panel must keep three logical columns

### Current screenshot

The latest screenshot shows an empty/partial state that visually collapses to approximately two columns:

```text
--        | 当前里程
          | --
```

The reference uses three stable metric groups:

```text
SOC        | 当前里程       | 最近行程
72%        | 12,674 km      | 24.2 km
progress   |                | 15.4 kWh/100km
```

### Required behavior

The panel geometry must remain stable even when data is missing.

For unavailable data:

```text
--%        | 当前里程       | 最近行程
--         | -- km          | -- km
progress   |                | -- kWh/100km
```

or equivalent truthful placeholders.

Rules:

- never fabricate values;
- do not delete the third column just because no completed trip exists;
- preserve both vertical separators;
- preserve relative column widths;
- keep SOC visually dominant on the left;
- keep the progress track present even when SOC is unavailable, using empty/muted treatment.

The visual skeleton should not jump when the user records the first trip.

## 7. P1 — Restore the Hero vehicle switch control

The current screenshot does not show the reference top-right vehicle selector.

Target:

- round dark translucent container;
- white car outline icon;
- small downward chevron;
- no text label;
- aligned inside the same Hero top inset as the identity block.

This is both a visual element and the correct multi-vehicle entry point.

## 8. P1 — Hero artwork crop/position tuning

The current BYD Seal artwork itself is good enough to retain.

Remaining crop differences:

- too much vertical space is allocated to the artwork because of the oversized Hero;
- the vehicle sits later in the vertical composition than the reference;
- the reference gives the car stronger visual dominance relative to the aurora-only area;
- the target keeps enough lower reflection to feel premium without allowing reflection/background to extend the Hero excessively.

### Crop priority order

1. preserve complete vehicle silhouette;
2. preserve front bumper and wheels;
3. preserve enough water/reflection to ground the car;
4. preserve aurora behind the roofline;
5. sacrifice excess sky before increasing Hero height.

Do not distort the artwork aspect ratio.

## 9. P1 — Recent Trip card needs two visual states

The current screenshot is a legitimate no-trip state, but its structure is much shorter and simpler than the populated reference card.

### 9.1 Populated state — must match reference

When a completed trip exists, show:

- circular route icon + `最近行程`;
- `查看全部` + chevron on the right;
- start/end route rail;
- origin → destination;
- timestamp;
- `已完成` status pill;
- five metric groups: distance / duration / consumption / SOC change / mileage change.

### 9.2 Empty state — must remain truthful but structurally compatible

When no completed trip exists:

- keep the same outer card width, radius and header alignment;
- keep the route icon and `最近行程` title;
- a concise empty message is allowed;
- do not fabricate route or metrics;
- avoid making the empty card so visually unrelated that the page geometry changes dramatically after the first trip.

Recommended empty-state goal: preserve a predictable card footprint while omitting facts that do not exist.

## 10. P0 — Monthly Energy card must be fully visible in the first viewport

### Current screenshot

The Monthly Energy card begins correctly, but the bottom navigation arrives before the card's metric body is visible. The user can see roughly:

```text
ENERGY FLOW
本月能源                 0 次
```

but not the full reference content.

### Reference first viewport

The first viewport includes:

```text
ENERGY FLOW                      0 次充电 >
本月能源

本月用电       本月费用       平均电价
0.0 kWh        ¥ 0.00         ¥ 0.00/kWh

[ slim energy/progress line ]
```

### Required change

Recover vertical space primarily from:

1. Hero height;
2. Hero → Recent Trip gap;
3. Recent Trip → Energy gap;
4. overly generous card internal padding if still required.

Do not remove Monthly Energy metrics to make the screenshot fit.

## 11. P1 — Monthly Energy header action mismatch

Current visible action:

```text
0 次
```

Target:

```text
0 次充电  >
```

Required:

- keep the count green;
- include `次充电`;
- add muted right chevron;
- align vertically with the card title group;
- tapping it should route to charging records/statistics according to existing product navigation.

## 12. P1 — Card vertical rhythm is too loose

The latest screenshot has noticeably larger gaps between major cards than the approved reference.

Target starting point:

```text
Hero -> Recent Trip:      ~8dp
Recent Trip -> Energy:    ~6–8dp
```

The Dashboard should feel dense but not cramped.

Do not use a generic ~24dp `lg` vertical arrangement between all Dashboard cards.

## 13. P1 — Bottom navigation is close, but needs dock geometry tuning

Already correct:

- five semantic items;
- selected `总览` is green;
- unselected icons/labels are muted;
- no bright Material selected pill.

Still to tune:

- make the nav background read as a distinct dark dock rather than a flat continuation of page black;
- match the reference top-left/top-right rounding;
- preserve safe-area/inset behavior;
- keep icon/label vertical spacing consistent;
- do not increase nav height to compensate for content geometry elsewhere.

## 14. P2 — Typography differences to tune after layout lock

Typography is already directionally close. Do **not** spend time micro-tuning it before P0 geometry is fixed.

After layout stabilizes, compare:

- `MY EV` letter spacing and baseline;
- brand-to-model vertical spacing;
- Hero model weight;
- card title size/weight;
- metric unit scaling;
- secondary gray text brightness;
- bottom navigation label weight.

The reference relies more on scale/color hierarchy than on heavy bold weights.

## 15. P2 — Color and glass tuning

Current dark/green palette is close enough to keep.

Remaining visual tuning should be performed by screenshot overlay:

- top Hero should not be covered by an opaque green slab;
- glass panel should remain translucent enough to retain artwork context;
- card outlines should be barely visible but present;
- dividers should not become bright gray lines;
- green should remain energetic without appearing neon-yellow.

Do not introduce new hard-coded green variants throughout individual components. Tune centralized tokens unless a local alpha treatment is required.

## 16. Data state vs visual acceptance

The target reference contains real-looking populated data while the current screenshot contains missing SOC/mileage/trip values.

Production behavior must remain truthful. Therefore visual acceptance should use **two screenshots**:

### A. Populated visual-fixture state

Use deterministic local preview/test/sample data to verify the 1:1 geometry:

- SOC present;
- mileage present;
- one completed trip present;
- monthly energy values present or zero values rendered in full metric layout.

This fixture exists only for screenshot/preview acceptance and must not leak fabricated facts into production runtime state.

### B. Real empty/partial runtime state

Verify:

- placeholders remain truthful;
- component geometry remains stable;
- no metric columns disappear unexpectedly;
- no card collapses enough to destroy Dashboard visual rhythm.

Both states are required before visual acceptance is complete.

## 17. Recommended implementation order

Do the next code pass in this order:

1. **Hero outer geometry** — outer gutter and target height.
2. **Hero composition** — image fills full card; remove separate opaque identity region.
3. **Hero overlay content** — identity top-left and vehicle switch top-right.
4. **Hero glass panel** — lock fixed three-column structure.
5. **Dashboard vertical spacing** — reduce major card gaps.
6. **Recent Trip populated layout** — align to reference.
7. **Recent Trip empty layout** — preserve truthful compatible footprint.
8. **Monthly Energy full body** — ensure all metrics are first-viewport visible.
9. **Bottom navigation dock shape** — visual polish only after viewport geometry fits.
10. **Typography/color micro-tuning** — final overlay pass.

Do not start with icon replacement or color micro-adjustments while Hero height/composition is still wrong.

## 18. Screenshot acceptance checklist

For each implementation iteration capture a screenshot at the same representative device size and compare against the approved reference.

### Overlay checkpoints

- [ ] outer card x positions align;
- [ ] Hero top/bottom positions align;
- [ ] no hard identity/artwork seam exists;
- [ ] model baseline is close to reference;
- [ ] vehicle visual center/scale is close;
- [ ] glass panel top/bottom positions align;
- [ ] three Hero metric columns align;
- [ ] Hero → Recent Trip gap aligns;
- [ ] Recent Trip card height aligns in populated state;
- [ ] Energy card starts at the correct vertical region;
- [ ] Energy metrics and progress are visible before nav;
- [ ] bottom navigation top boundary aligns;
- [ ] selected/unselected nav color hierarchy matches;
- [ ] card radii and dark surface contrast match.

### Acceptance severity

**Blocker:**

- Hero still split into solid header + artwork;
- Hero remains so tall that Energy metrics are not visible;
- Hero state panel collapses from three columns;
- bottom nav/content overlap hides required target content;
- major card outer x positions differ visibly.

**Needs tuning but not architecture rework:**

- a few dp of padding;
- minor text baseline differences;
- small icon shape mismatch;
- green intensity;
- divider alpha;
- corner radius micro-difference.

## 19. Current decision

The latest screenshot is **good enough to keep as the implementation base**.

Do not restart the design or replace the BYD Seal artwork.

The next iteration should be a focused convergence pass against this checklist, with the highest leverage changes being:

```text
continuous Hero artwork
+ correct Hero height
+ narrower Dashboard gutter
+ stable three-column state panel
+ full Energy card visible above bottom nav
```

Once those are correct, the page should move from "visually similar" to "reference-matched" without large architectural changes.
