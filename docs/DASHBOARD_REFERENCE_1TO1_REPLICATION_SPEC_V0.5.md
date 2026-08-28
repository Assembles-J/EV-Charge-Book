# EV Charge Book v0.5 Dashboard 1:1 Replication Specification

Date: 2026-08-28  
Status: **Implementation baseline — reproduce the supplied reference screenshot 1:1**  
Reference source: user-supplied Android screenshot, **941 × 1672 px**  
Target page: Dashboard / `总览`

> This document is intentionally prescriptive. For this Dashboard pass, the supplied screenshot is the visual source of truth. Existing generic design tokens are secondary where they conflict with this reference.

## 1. Goal and acceptance rule

The implementation goal is not to create a similar dark EV dashboard. It is to reproduce the supplied reference as closely as Android/Compose and device rendering allow.

Acceptance requires matching all of the following:

- information hierarchy;
- component order;
- card proportions;
- horizontal and vertical spacing;
- corner radii;
- dark teal/black surface treatment;
- green accent intensity;
- typography scale and weight hierarchy;
- icon scale and placement;
- Hero image crop and visual prominence;
- bottom navigation proportions;
- divider positions;
- data alignment inside metric groups.

A result that is functionally correct but visibly rearranged, simplified, or restyled is **not accepted**.

## 2. Reference coordinate system

All measurements below were extracted from the supplied **941 × 1672 px** screenshot. Pixel coordinates are reference-image coordinates, not Android dp.

For initial Compose tuning, use a 360 dp-wide content viewport as the normalization baseline:

```text
scaleX = 360 / 941 = 0.3826 dp per reference px
```

The screenshot aspect ratio is approximately `0.5628`, close to a modern tall Android viewport. Vertical conversion should therefore be treated as a starting estimate rather than a fixed density rule; final acceptance is screenshot overlay on a real/emulated target device.

### Major regions

| Region | Reference bounds (px) | Approx. 360dp viewport bounds | Notes |
|---|---:|---:|---|
| Hero vehicle card | x 20–922, y 95–846 | x 7.7–352.8, y 36–324 | dominant visual region |
| Hero glass stats panel | x 49–892, y 645–824 | x 18.7–341.2, y 247–315 | translucent inset panel |
| Recent trip card | x 20–922, y 867–1173 | x 7.7–352.8, y 332–449 | secondary content card |
| Monthly energy card | x 20–922, y 1188–1530 | x 7.7–352.8, y 455–586 | third card |
| Bottom navigation visual region | x 0–941, y ~1497–1672 | full width, ~67dp visual height in normalized screenshot | overlays/occupies lower viewport |

The recurring visible side margin is about **20 px**, corresponding to about **7.7 dp** in the 360dp normalization. In production Compose this may resolve to 8dp depending on system width and screenshot density.

## 3. Page hierarchy

The Dashboard visible order is exactly:

1. system status bar;
2. Hero vehicle card;
3. Recent trip card;
4. Monthly energy card;
5. persistent five-item bottom navigation.

Do **not** insert a separate app title/header above the Hero.

The current Dashboard code already suppresses `DashboardBrandHeader`; keep that behavior.

## 4. Global visual language

### 4.1 Background

The full page is near-black with a subtle green-blue cast.

Recommended base:

```text
#010305 to #090D0C
```

Use a flat dark base. Do not add decorative page gradients outside the Hero artwork.

### 4.2 Surface cards

Cards are not medium-gray Material surfaces. They are very dark blue-green glass-like panels with low-contrast outlines.

Suggested starting tokens:

```text
Page background:       #030708
Card surface:          #091216 / #0A1317
Raised/glass surface:  #0C171C with alpha where image is behind it
Primary text:          #F0F4F2
Secondary text:        #9AA5A2
Hairline/divider:      rgba(150, 175, 170, 0.20)
Accent green:          approx #3AEA83
Success green:         #32E383
Danger/start marker:   soft coral approx #FF7373
```

The extracted dominant green family in the supplied reference clusters around RGB `(58, 234, 131)`, i.e. approximately **#3AEA83**. Existing `EVDesignTokens.Energy.green = #32F080` is close and may remain the implementation token if overlay comparison confirms it.

### 4.3 Card outlines

Use a very subtle 1dp-equivalent border. It must be visible against the page background without looking like a bright stroke.

### 4.4 Corner radii

Visual family:

- large card: ~28–32 reference px radius;
- Hero card: slightly larger, ~36–42 reference px radius;
- inner glass panel: ~28–34 reference px radius;
- circular/icon containers: fully rounded.

Compose starting points:

```text
Hero outer: 20–24dp
Standard card: 18–20dp
Inner glass panel: 18–20dp
Pills: 50%
```

Do not use small 10–12dp cards for the Dashboard.

## 5. Hero vehicle card

The Hero is the main identity surface and visually occupies about 45% of the screenshot above the navigation.

### 5.1 Outer container

Characteristics:

- full-width card with narrow outer margin;
- clipped large rounded rectangle;
- no visible elevation shadow;
- background is the finished vehicle artwork itself;
- artwork contains aurora, night sky, mountains, water and reflection;
- no extra rectangular image frame inside the Hero.

This reinforces the existing approved direction: aurora/reflection/glow belongs to the generated image asset, not to Compose effects.

### 5.2 Hero artwork behavior

Required:

- use the selected vehicle's finished Hero asset;
- artwork fills the Hero card edge to edge;
- use crop/scale strategy that preserves the vehicle and lower reflection;
- do not center-crop so aggressively that the car wheels, lower bumper, or glass panel relationship changes;
- do not reconstruct aurora, mountains, water reflection, glow or vignette in code.

The screenshot shows the vehicle centered horizontally but slightly biased to the lower-middle. The car body occupies approximately the middle 65–70% of Hero width and overlaps visually with the dark lower state area.

### 5.3 Top-left identity block

Order:

```text
●  MY EV

比亚迪
海豹 2025款
```

Visual hierarchy:

- green dot: small, bright, about 14–16 reference px diameter;
- `MY EV`: uppercase, white, medium weight, generous tracking;
- brand: smaller than model, white;
- model: dominant white title, bold/semibold;
- no additional subtitle under model.

Approximate target type scale on a 360dp-wide baseline:

```text
MY EV:       12–13sp medium
Brand:       14–16sp regular/medium
Model:       25–28sp semibold
```

Do not substitute the Hero identity with a generic page title.

### 5.4 Top-right vehicle switch control

The reference uses a round dark translucent control containing:

- white outline car icon;
- small downward chevron.

Requirements:

- circular/translucent dark background;
- icon is horizontally centered as a grouped car+chevron unit;
- no text label;
- tap opens vehicle selection.

Suggested size: ~48–52dp outer diameter on 360dp normalization.

### 5.5 Lower glass state panel

The Hero contains a large inset glass panel near the bottom rather than separate floating cards.

Layout: three columns.

#### Column A — SOC

```text
72%
[ green progress bar ]
```

Rules:

- `72` is the largest value in the panel;
- number is green;
- `%` is white and smaller;
- do not show a `当前 SOC` heading next to the percentage;
- progress line is slim, rounded, dark track with green fill;
- optional one-time fill animation only; no looping glow.

#### Column B — current mileage

Header row:

- small odometer/gauge icon;
- `当前里程` secondary label.

Value:

```text
12,674 km
```

Rules:

- numeric value large white;
- unit smaller and visually subordinate;
- vertical divider on both sides of the metric region as shown.

#### Column C — latest trip

Header row:

- route icon;
- `最近行程` secondary label.

Values:

```text
24.2 km
15.4 kWh/100km
```

Rules:

- trip distance is primary white value;
- consumption is smaller;
- consumption numeric value is green, unit muted gray.

### 5.6 Hero data truthfulness

Maintain existing product rules:

- current SOC comes from persisted current vehicle state;
- current mileage comes from persisted current vehicle state;
- latest-trip distance and consumption come from the latest completed trip;
- unavailable facts show `--` or are omitted rather than fabricated.

The screenshot values are visual examples, not hard-coded production data.

## 6. Recent trip card

### 6.1 Header

Left side:

- circular green-tinted route icon container;
- title `最近行程`.

Right side:

- green text `查看全部`;
- muted chevron-right.

The header is a single horizontally aligned row.

### 6.2 Route block

Left rail shows:

- green start marker;
- subtle dotted/dashed connecting line;
- coral/red end marker.

Main route text:

```text
张江高科 → 奉贤南桥
```

Below:

```text
今天 07:42
```

Status pill is positioned to the right of the route line:

```text
已完成
```

Pill uses a very dark green translucent background and green text. It is compact, not a filled bright-green chip.

### 6.3 Metrics row

Exactly five logical metric groups are visible:

1. distance;
2. duration;
3. consumption;
4. SOC change;
5. mileage change.

Reference content:

```text
24.2       1:23       15.4       78% → 72%       12,650 → 12,674
km         时长       kWh/100km  SOC 变化         里程 (km)
```

Vertical separators divide metric groups.

Rules:

- numeric line is brighter/larger;
- caption line is muted;
- arrows are green;
- do not collapse these into only 2–3 metrics on normal-width phones;
- if a device is narrower, reduce spacing/type carefully before changing information order;
- if wrapping becomes unavoidable, maintain grouping and visual hierarchy rather than deleting facts.

### 6.4 Interaction

- card tap may open latest trip detail;
- `查看全部` opens trip history/list;
- no edit/delete controls on the Dashboard card itself.

## 7. Monthly energy card

### 7.1 Header

Left:

- circular green-tinted bolt container;
- small uppercase overline `ENERGY FLOW`;
- main title `本月能源`.

Right:

```text
0 次充电  >
```

The count is green and the chevron is muted.

### 7.2 Main metrics

Three horizontal columns separated by vertical dividers:

1. 本月用电;
2. 本月费用;
3. 平均电价.

Reference:

```text
本月用电          本月费用          平均电价
0.0 kWh           ¥ 0.00            ¥ 0.00/kWh
```

The first metric is visually most prominent:

- `0.0` large white;
- `kWh` green;
- other values white with smaller units.

### 7.3 Bottom progress / energy line

At card bottom:

- long dark rounded track;
- small green luminous point/fill at the far left when monthly energy is zero/minimal.

Keep the treatment subtle. Do not use a thick Material progress indicator.

## 8. Bottom navigation

The reference bottom navigation contains five evenly spaced items:

```text
总览   记录   统计   行程   车辆
```

Current code already has this exact semantic order and icon families; preserve it.

### 8.1 Visual treatment

- background: very dark blue-green, visually separate from page content;
- top corners appear strongly rounded due to a dock-like container treatment;
- selected item: bright green icon + green label;
- unselected items: medium gray icon + gray label;
- no Material selected pill/indicator;
- icons are relatively large and labels have comfortable spacing below them.

The current implementation already sets the selected indicator transparent; retain that.

### 8.2 Recommended sizing

Starting points:

```text
Nav visual height: ~72–82dp including safe-area treatment
Icon: 24–28dp
Label: 11–12sp
Selected tint: #32F080 / tuned to reference
```

Avoid a flat default Material NavigationBar appearance if it fails to reproduce the dock geometry in the screenshot.

## 9. Typography hierarchy

The screenshot uses a compact modern Android sans-serif style. Use system sans / current product font; visual size and weight are more important than introducing a custom font.

Suggested hierarchy:

| Role | Target |
|---|---|
| Hero model | 25–28sp Semibold |
| Hero SOC number | 28–32sp Medium/Semibold |
| Card title | 18–20sp Semibold |
| Major metric | 19–24sp Regular/Medium |
| Route title | 15–17sp Medium |
| Body/value | 14–16sp Regular |
| Secondary label | 11–13sp Regular |
| Overline | 10–11sp Medium, uppercase |
| Bottom nav label | 11–12sp Medium |

Do not globally force bold typography. The reference gains hierarchy mainly through size, color and spacing.

## 10. Iconography

Use simple rounded-outline icons close to the reference. Material icons are acceptable where geometry is sufficiently close.

Visible icon concepts:

- car / vehicle switch;
- odometer/gauge;
- route/network;
- route/history;
- bolt;
- home;
- history/records;
- bar chart;
- route;
- car.

Icon color rules:

- action/selected/energy: green;
- primary Hero control: white;
- secondary navigation: muted gray.

Do not mix filled high-detail icons with outline icons in the same card unless the reference does so.

## 11. Spacing rules

Although exact values must be verified with screenshot overlay, the reference follows these patterns:

- page/card side gutter: ~8dp normalized;
- gap Hero → recent trip: ~8dp;
- gap recent trip → monthly energy: ~6–8dp;
- standard inner card padding: ~16–20dp;
- Hero identity inset: ~16–18dp from left/top;
- Hero glass inset: ~12–14dp from outer card edges;
- metric separators: centered between groups, not touching card top/bottom;
- headings and sublabels use tight 2–6dp vertical rhythm.

Existing Dashboard `LazyColumn` spacing of `lg` is likely too generous for the supplied reference if `lg` resolves near 24dp. Tune Dashboard-specific vertical spacing to the reference rather than applying generic page spacing blindly.

## 12. Current code mapping

The present repository already has the correct high-level Dashboard component split:

```text
DashboardScreen.kt
  ├─ HeroVehicleCard.kt
  ├─ DashboardRecentTripCard.kt
  └─ EnergyCockpitCard.kt

MainActivity.kt
  └─ five-item bottom NavigationBar
```

Implementation should primarily tune these existing components instead of creating a parallel Dashboard architecture.

### Expected change locations for the future implementation PR

- `android/app/src/main/java/com/evchargebook/ui/dashboard/HeroVehicleCard.kt`
- `android/app/src/main/java/com/evchargebook/ui/dashboard/DashboardRecentTripCard.kt`
- `android/app/src/main/java/com/evchargebook/ui/dashboard/EnergyCockpitCard.kt`
- `android/app/src/main/java/com/evchargebook/ui/dashboard/DashboardScreen.kt`
- `android/app/src/main/java/com/evchargebook/MainActivity.kt` for bottom-nav dock geometry if needed
- `android/app/src/main/java/com/evchargebook/ui/theme/EVDesignTokens.kt` only for genuinely reusable token corrections

Do not spread one-off screenshot constants throughout unrelated screens.

## 13. Responsive behavior

The screenshot is the baseline composition. Responsiveness must preserve that composition rather than redesign it.

Priority when width is constrained:

1. preserve side gutters and card shape;
2. preserve all metric groups;
3. reduce inter-column spacing;
4. slightly reduce secondary-label size;
5. allow controlled text ellipsis for long locations;
6. only then allow a secondary layout variant.

For larger screens, do not stretch card inner content indefinitely. Preserve the mobile proportions with a reasonable max content width.

## 14. System bars and edge-to-edge

The app currently uses edge-to-edge. The target screenshot shows content beginning below a dark status bar and a visually integrated bottom navigation area.

Requirements:

- status icons remain readable white;
- no bright system-bar background;
- navigation dock/safe area must not clip labels;
- bottom content must scroll behind/above the dock correctly without becoming inaccessible.

## 15. Motion

Allowed:

- Hero SOC progress fill once on entry, approximately 500–700ms;
- standard screen/navigation transitions if already present.

Not allowed:

- looping pulse;
- looping glow;
- animated aurora;
- running lights;
- flashing card borders;
- decorative particle motion.

The Hero image provides the visual drama; Compose should remain restrained.

## 16. Data-state variants

The visual layout must remain stable across realistic states.

### Vehicle state missing

- keep Hero structure;
- replace unknown SOC/mileage with `--`;
- do not invent values.

### No completed trip

- keep recent-trip card footprint coherent;
- show a restrained empty message while preserving the same card visual language;
- Hero latest-trip metric may show `--`.

### No charging this month

- show `0 次充电`, `0.0 kWh`, `¥ 0.00`, `¥ 0.00/kWh` as appropriate from real computed data;
- preserve the thin bottom energy track.

## 17. 1:1 verification procedure

Implementation PR acceptance must include an image comparison, not only code review.

Required workflow:

1. run the Dashboard on a target Android device/emulator with the same sample data presentation as the reference where practical;
2. capture a full-screen screenshot;
3. scale/crop the implementation screenshot to the same visible viewport as the reference;
4. overlay at 50% opacity or use pixel-difference tooling;
5. correct major geometry first: card bounds, Hero crop, glass panel, metric columns, bottom nav;
6. then correct typography and color;
7. repeat until visual drift is minor and localized.

### Acceptance tolerances

Use these as engineering tolerances, not an excuse for visible mismatch:

- major card edges: within ~4 reference px after normalized overlay;
- repeated horizontal alignment: within ~4 reference px;
- icon center placement: within ~6 reference px;
- typography baseline: within ~6 reference px;
- color: perceptually close under the same display/theme;
- Hero artwork crop: vehicle body/wheels/reflection must visually align, not merely be present.

## 18. Explicit non-goals

This specification does **not** authorize:

- redesigning the screenshot into a generic Material 3 page;
- replacing the Hero with a plain car cutout on a flat background;
- moving monthly energy above recent trip;
- adding new Dashboard features;
- adding AI/battery prediction widgets;
- removing metric groups for implementation convenience;
- adding a top app toolbar;
- duplicating vehicle specifications already moved to the Vehicle page.

## 19. Relationship to existing docs

This specification narrows and operationalizes the existing approved Dashboard direction.

Where documents differ:

1. this 1:1 screenshot specification governs Dashboard geometry and appearance;
2. `DASHBOARD_APPROVED_VISUAL_BASELINE_V0.5.md` governs product/content boundaries;
3. `EVChargeBook_UI_Design_Language_v0.5.md` remains the broader cross-screen design language.

The existing approved rules remain in force: finished Hero artwork, dark-first presentation, current vehicle state, recent completed trip, restrained motion, and real-device acceptance.

## 20. Definition of done

The Dashboard replication pass is done only when:

- [ ] Hero overall dimensions and crop visually match the supplied reference;
- [ ] `MY EV`, brand/model identity and vehicle-switch control align with the reference;
- [ ] Hero glass panel reproduces the three-column SOC / mileage / latest-trip layout;
- [ ] recent-trip card reproduces route rail, status pill and five metric groups;
- [ ] monthly-energy card reproduces header, three metrics and bottom thin energy track;
- [ ] bottom navigation reproduces the five-tab dock styling and selected state;
- [ ] typography, green accent, surfaces, outlines and radii are tuned against screenshot overlay;
- [ ] unavailable data remains truthful;
- [ ] CI passes;
- [ ] a physical-device or representative emulator screenshot comparison is attached to the implementation PR;
- [ ] no known major visual mismatch remains.

Until the screenshot comparison is complete, code completion alone is **not** visual acceptance.
