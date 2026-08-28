# EV Charge Book v0.5 Dashboard Approved Visual Baseline

Date: 2026-08-28
Status: Approved visual baseline for Android implementation

## Dashboard information roles

The Dashboard is split into three clear responsibilities:

1. **Vehicle Hero** — answers: "What is my vehicle's current state?"
2. **Monthly Energy** — answers: "How much energy/cost did I use this month?"
3. **Recent Trip** — answers: "What happened on my latest drive?"

Do not mix static vehicle specification facts into the dynamic Dashboard Hero.

## Vehicle Hero

Approved direction:

- Dark First + restrained green accent.
- No `ACTIVE` badge.
- Vehicle artwork is a finished generated asset and should visually fill the Hero width.
- Do not place the vehicle inside a second inset rectangular image frame.
- Compose must not recreate aurora/reflection/glow effects; those belong in the finished artwork asset.
- Bottom state panel uses a restrained translucent glass-like surface.

### Dynamic facts

Hero state panel shows:

- current SOC as the dominant value, without a `当前 SOC` title beside the percentage;
- a slim SOC progress line with one subtle entry fill animation;
- current mileage;
- latest completed Trip distance and estimated average consumption when available.

Unknown facts remain `--` or are omitted. Do not fabricate current state.

### Static facts removed from Dashboard Hero

The following belong on the Vehicle page instead:

- battery capacity;
- rated range;
- static vehicle specification data.

## Motion

Allowed motion is intentionally minimal:

- SOC linebar may animate from empty to current value on entry, roughly 500–700 ms.
- no pulse, running-light, flashing ring, or decorative looping animation.

## Recent Trip card follow-up

The standalone recent Trip card should prioritize:

- start -> end location presentation;
- distance;
- elapsed duration;
- start/end SOC;
- start/end mileage;
- recorded estimated kWh/100km when available.

Detailed GPS diagnostics remain on Trip detail, not Dashboard.

## Acceptance boundary

Code/CI completion is not physical visual acceptance. Final closeout still requires a real-device pass for scale, crop, spacing, readability and asset fit.

Related: #70, #87, #89, #94, #95.
