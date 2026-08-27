# EV Charge Book UIUX Convergence Roadmap

Version: v1.0
Status: Active UIUX Coordination Document

## Current Phase

The project has moved from UI redesign into UI reliability hardening.

The current product priority is:

Trip Reliability RC -> UIUX convergence -> Production candidate

UI work must support data trust and daily usability. Avoid expanding UI scope into new product features.

## Completed UIUX Work

- Cockpit visual language established
- ENERGY / TRIP / CHARGE surfaces unified
- Excessive card stacking and AI-generated style removed
- Trip cockpit responsive layout
- Small screen adaptation
- Large font scale adaptation
- Form dirty-state protection
- IME and keyboard handling improvements
- Warning semantic colors
- Global empty state / navigation polish

## Current UIUX Hardening Tasks

### P1: Cockpit Theme Tokens

Goal:

Replace direct Material inverse color usage with dedicated cockpit tokens.

Target:

- Cockpit background
- Primary text
- Secondary text
- Electric accent
- Warning state
- Error state

Reason:

Cockpit pages must remain visually stable across Android light mode, dark mode and dynamic color changes.

## P1: Trip Diagnostic Presentation

Trip reliability data should be understandable by users.

Future UI should present:

- GPS health
- Long gap information
- Location source distribution
- Recovery events
- Data confidence

A completed Trip does not automatically mean continuous GPS quality.

## P2: Accessibility

Continue improving:

- TalkBack descriptions
- Icon semantic labels
- 48dp minimum touch targets
- State descriptions that do not rely only on color

## P2: Real Data Robustness

Protect layouts against:

- Long vehicle names
- Long charging locations
- Long Bluetooth names

Use truncation and predictable layout behavior.

## Future Release Alignment

UIUX work should align with:

Trip Reliability RC

Not current priorities:

- Cloud Sync UI
- MapLibre expansion
- OBD UI
- AI analysis UI

## Agent Rules

Allowed:

- UI token refactoring
- Accessibility improvements
- Trip diagnostic UI
- UX polish

Avoid:

- New product features
- Large architecture changes
- UI expansion unrelated to current release milestone

## Next Milestone

Trip Reliability RC UI completion:

1. Cockpit token system
2. Trip diagnostic summary
3. Accessibility baseline
4. Real-device verification support
