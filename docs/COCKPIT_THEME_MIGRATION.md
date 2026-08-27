# Cockpit Theme Token Migration

## Purpose

Introduce stable EV cockpit visual tokens before replacing existing Material inverse color usage.

## Current state

Cockpit pages currently use Material inverse colors for dark panels.

This works visually but couples product identity to Material theme semantics.

## Target

Migrate vehicle-style panels to dedicated tokens:

- background
- primaryText
- secondaryText
- accent
- warning
- danger

## Scope

Included:

- Dashboard energy panel
- Trip live cockpit
- Trip summary
- Charge ledger
- Stats summary
- Vehicle preview

Not included:

- business logic
- Trip reliability model
- database
- sync

## Migration order

1. Add token layer
2. Replace UI usages incrementally
3. Validate light/dark mode
4. Add accessibility semantics

## Principle

Cockpit visuals are product identity, not Material inverse surfaces.
