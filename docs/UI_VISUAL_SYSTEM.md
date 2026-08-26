# EV Charge Book UI Visual System

## Goal

EV Charge Book should feel like a calm, native mobile product rather than a generic AI-generated dashboard.

The visual direction combines four shipped-product patterns:

- **Tesla**: vehicle-first hierarchy and clear primary actions
- **Revolut**: compact financial summaries and readable numeric emphasis
- **Strava**: trip/activity information hierarchy
- **Linear**: restrained spacing, low visual noise, lists, settings and forms

These are references for design principles only. Do not reproduce branded assets or exact proprietary layouts.

## Core rules

1. Content hierarchy comes before decoration.
2. Use one primary action per screen where practical.
3. Prefer typography, spacing and subtle dividers over nested cards.
4. Normal screens must not use oversized hero headers.
5. Default horizontal content padding is 16dp.
6. Common vertical relationships use 4dp, 8dp, 12dp and 16dp.
7. 20-24dp spacing is reserved for major section separation.
8. Default surface radius is 14dp; large summary surfaces may use 18dp.
9. Avoid strong shadows. Prefer a 1dp low-contrast outline when separation is needed.
10. Keep Android-native interaction behavior and Material 3 accessibility.

## Color

The app owns a stable restrained green energy accent rather than inheriting Android dynamic colors.

- Background: near-neutral gray
- Surface: neutral white / dark surface
- Primary: restrained deep green
- Primary container: very light green used for one important summary area
- Borders: low-contrast neutral gray

Do not add gradients, glass effects, decorative blobs or multiple competing accent colors.

## Typography

- Page title: 22-28sp, semibold
- Section title: 20sp, semibold
- Card/list title: 16sp, semibold
- Body: 14-16sp
- Numeric values should be emphasized with typography before using special containers.

Avoid giant 32sp+ titles on normal business screens.

## Screen mapping

### Dashboard

Reference behavior: Tesla + Revolut.

- Vehicle selector sits directly below a compact page title.
- One primary monthly-energy summary surface.
- Cost and average price are compact secondary metrics.
- Recent charging records use simple rows/surfaces.
- Do not reintroduce the old black circular-progress hero.

### Charging records

Reference behavior: Linear + Revolut.

- Compact top bar.
- One small aggregate summary.
- Individual records prioritize location, cost, energy and SOC.
- Avoid icon-heavy colored cards for every record.

### Trip

Reference behavior: Strava.

- Map/route is the visual focus when route data exists.
- Distance, duration and timestamps are primary metrics.
- Controls should remain visually separate from analytics.

### Vehicle and settings

Reference behavior: Tesla + Linear.

- Current vehicle gets a subtle selected state.
- Secondary actions use text buttons or rows rather than large cards.
- Bluetooth, backup and future settings use consistent setting rows.

### Forms and detail pages

Reference behavior: Linear + native Android.

- Compact top app bar.
- 16dp horizontal padding.
- No hero header.
- Group related fields by spacing and labels rather than wrapping every field group in another card.

## Explicit anti-patterns

Do not use:

- cards inside cards
- large unexplained top whitespace
- oversized page titles
- default gradients
- glassmorphism
- decorative floating shapes
- excessive pill components
- heavy elevation on every item
- different visual language for every feature
- web dashboard layouts transplanted directly to mobile

## Review checklist

Before merging a UI change, verify:

- Does the page still work without decorative elements?
- Is the most important value/action obvious within two seconds?
- Can at least one container be removed and replaced by spacing/dividers?
- Are ordinary page paddings near 16dp rather than 24-40dp?
- Is there only one strong accent area on the screen?
- Does dark mode preserve hierarchy without introducing a new style?
- Does the screen look consistent with Dashboard, Records and Vehicle screens?
