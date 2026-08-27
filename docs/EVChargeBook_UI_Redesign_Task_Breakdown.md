# EV Charge Book v0.5 UI Redesign Task Breakdown

## Goal

Turn the current function-first Compose UI into a polished EV companion experience without adding features outside the v0.5 mainline.

## Visual direction

- Dark First by default
- Energy green as the primary accent
- Vehicle-first hierarchy
- Fewer nested cards
- Real charts and timelines where data exists
- No fabricated live SOC, live range, or AI content

## P0 — current slice

### Theme foundation
- [x] EV design tokens
- [x] Dark First default theme
- [x] Light theme mapping retained for later in-app switching
- [x] Compatibility warning color mapping

### Dashboard
- [x] Replace old overview header with vehicle-first hero
- [x] Remove fabricated SOC / live range values
- [x] Add a graphical EV vehicle stage instead of a generic metric-card layout
- [x] Show stored battery capacity and rated range as real vehicle facts
- [x] Add branded EV Charge Book header
- [x] Replace nested energy metric tiles with one Energy Flow surface
- [x] Add a real six-month charging-energy visualization from monthlyTrend
- [x] Replace recent charging cards with a timeline layout
- [x] Make the empty charging state compact and part of the timeline
- [x] Keep one clear charging action on the dashboard

### Remaining dashboard polish
- [ ] Replace the temporary drawn vehicle silhouette with an approved reusable vehicle visual asset when available
- [ ] Replace Material NavigationBar selection treatment with a custom EV bottom bar
- [ ] Tune typography and spacing after another real-device screenshot pass
- [ ] Verify small / large Android screen behavior

## P0 — next pages after dashboard direction is accepted

### Charging records
- [ ] Full charging timeline
- [ ] Charger type differentiation
- [ ] SOC / energy / fee hierarchy using only recorded fields
- [ ] Dense empty state and first-record CTA

### Trip
- [ ] Route/map becomes the page visual anchor
- [ ] Trip summary metrics use a flat hierarchy instead of nested cards
- [ ] Speed and energy visuals use existing runtime data only
- [ ] Recent trips become an activity timeline/list

## P1

### Stats / energy
- [ ] Reuse Energy Flow visual language
- [ ] Monthly trend chart hierarchy
- [ ] Charger-type distribution
- [ ] Charging place analysis

### Vehicle
- [ ] Vehicle identity stage
- [ ] Vehicle metadata hierarchy
- [ ] Connection / backup tools visually separated from vehicle identity
- [ ] In-app Dark / Light mode switch

## P2

- [ ] Motion and number transitions
- [ ] Empty-state illustration polish
- [ ] Custom bottom navigation interaction
- [ ] Final visual QA screenshots

## Acceptance rule

A page should not be considered visually complete only because it uses Dark colors. It must have a clear visual anchor, hierarchy, and data visualization appropriate to the business function.
