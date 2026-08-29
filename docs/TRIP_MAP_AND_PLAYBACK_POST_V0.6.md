# Trip map context and trajectory playback — post-v0.6 authority

Status: implementation / follow-up authority after the Trip v0.6 UI closeout baseline.

This document covers **post-v0.6 enhancements** requested from the 2026-08-29 physical-device review. It must not be used to reopen or delay the existing #145 v0.6 visual acceptance or #77 lock-screen/background reliability acceptance.

Owning issues:

- #192 — interactive map context
- #193 — truthful trajectory playback
- #199 — map-provider validation spike

## Product boundary

The Trip route remains a record of real persisted GPS facts.

These follow-ups may improve presentation, camera interaction and playback, but they must not:

- invent a destination
- road-snap or rewrite recorded points
- fabricate missing GPS samples
- bridge a real LONG_GAP as continuous driving
- turn Trip detail into turn-by-turn navigation
- make Trip recording depend on a map/tile network service

Persisted Trip coordinates remain authoritative WGS84 facts. Any display/provider conversion belongs only to the rendering adapter.

## A. Trajectory playback — #193

### Implemented code baseline

#### Timeline engine — #197 / PR #198

Merged to `main` as `382fd91` after Android Build #501 passed.

The renderer-independent `TripPlaybackTimeline` provides:

- real timestamp-based duration and seek frames
- bounded visual interpolation only between short consecutive real samples
- `TripContinuityRules.LONG_GAP_SECONDS` as the hard discontinuity
- explicit long-gap frames that hold the prior real point
- exact jump to the next real sample at its real timestamp
- non-monotonic input rejection rather than silent reordering
- compact 1x / 2x / 4x / 8x advancement support
- shortest-path bearing interpolation for a future directional marker

No schema, Room, foreground service or map-provider change was introduced.

#### Playback controls / moving marker — #200 / PR #201

Merged to `main` as `d9e180e` after Android Build #503 passed.

The completed Trip `轨迹` surface now owns a compact, collapsed-by-default `轨迹回放` entry with:

- play / pause
- restart
- elapsed / total time
- seek slider
- 1x / 2x / 4x / 8x speed selection
- a restrained directional moving marker
- played route in the existing primary green
- future route as low-alpha context
- existing green start marker and compact red completed flag
- explicit `GPS 缺口` state

The first implementation intentionally uses the existing no-basemap Canvas renderer so playback chronology is not coupled to a future map SDK.

### Mandatory gap behavior

For consecutive persisted samples whose real capture-time interval is `>= 120s`:

- draw no continuous route segment across the gap
- interpolate no marker position inside the missing interval
- while playback time is inside the gap, keep the marker at the previous real point and expose `GPS 缺口`
- at the next real capture timestamp, jump to that real point

This is presentation behavior only. Playback never creates a new TripPoint.

### Physical acceptance still required

Use at least one real completed 20–40 minute Trip with hundreds of points and verify:

- play / pause / restart responsiveness
- seek correctness
- visible speed difference at 1x / 2x / 4x / 8x
- moving-marker readability without glow/pulse
- played/future route hierarchy
- real long-gap hold/jump behavior
- 400–1000+ point performance
- tab switching / leaving detail never changes persisted Trip data
- basic TalkBack/control accessibility

Keep #193 open until this device pass is complete.

## B. Interactive map context — #192

### Requested behavior

The route surface should be able to become a real map-like viewport rather than a fixed geometry card:

- one-finger pan/drag
- pinch zoom
- optional standard double-tap zoom when supplied naturally by the renderer
- initial camera fit to the full recorded Trip
- compact `回到全程` action after the user pans/zooms away
- start/end/current markers remain anchored to their real coordinates
- road/street names and a restrained amount of nearby geographic context
- route remains visually stronger than the basemap

Recorded markers are **not draggable editing handles**. Moving the map viewport is allowed; moving a recorded endpoint as though it changed the GPS fact is not.

### Renderer direction

MapLibre-compatible rendering is the current preferred direction because the renderer supports normal pan/zoom gestures, camera state/animation and style-driven vector data without requiring Trip persistence to know a specific map vendor.

Keep the boundary:

```text
TripPoint / TripRouteGeometry
        -> TripMapAdapter
        -> MapLibre-compatible renderer
        -> MapStyleProvider
```

Playback from #193 must reuse the same adapter/timeline state when a map renderer lands. Do not build a second playback chronology inside the map layer.

### Basemap / labels

A production map is allowed to provide restrained context such as:

- common road/street names
- road geometry
- water / green space / major blocks
- a limited amount of useful nearby context

Avoid heavy POI density, destination pins, navigation blue, route pulses or ETA/turn instructions.

Dark First remains authoritative and `EVDesignTokens.Energy.green` remains the main route accent.

## C. Provider validation — #199

OpenFreeMap is currently a **prototype candidate only**, not a production approval.

Before the first production map integration, test one real Shanghai/Fengxian Trip and confirm:

- style/tile endpoint works on a normal mainland mobile network
- useful road labels are available around the real Trip
- Chinese labels appear where the source data supports them
- rendered route aligns with persisted WGS84 TripPoints
- dark map style remains subordinate to the Trip route
- required attribution remains visible
- map/tile failure is detectable
- failure falls back to the existing truthful no-basemap route renderer
- Trip recording and Trip data access remain independent from map availability

If this validation fails, change `MapStyleProvider`; do not change Trip persistence or GPS facts to fit the provider.

## Delivery order

1. Playback domain contract — complete (#197 / PR #198)
2. Playback UI on existing renderer — complete (#200 / PR #201)
3. Physical playback acceptance — pending (#193)
4. Provider/device validation — pending (#199)
5. Interactive map renderer / pan-zoom / fit-route — pending (#192)
6. Road labels / restrained nearby context after provider approval — pending (#192)
7. Reuse the same playback timeline on the map renderer — after #192 base map integration

## Acceptance separation

Code/CI, map-provider validation and physical-device UX are separate facts.

Do not mark #192 or #193 complete because compilation succeeds. Do not mark #145 incomplete merely because these post-v0.6 enhancements remain open.