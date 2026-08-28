# Vehicle Catalog Remote Maintenance and Offline-First Runtime

## Purpose

EV Charge Book uses the network to **refresh reference vehicle metadata only**. The network is not an availability dependency for normal application use.

Production catalog endpoint:

`https://groupim.cn/ev-charge-book/release-meta/vehicle-catalog-v1.json`

Protected management page:

`https://groupim.cn/ev-charge-book/hero-admin/`

## Authority model

1. The APK ships a small `assets/vehicle_catalog.json` fallback so a first launch with no network still has usable catalog entries.
2. Room `vehicle_catalog` is the runtime source shown by the app.
3. On process start, a separate application-scope IO coroutine may attempt to refresh the managed catalog.
4. The complete remote document is parsed and validated **before** any Room mutation.
5. A timeout, DNS failure, HTTP failure, unsupported schema, malformed item, duplicate ID, or empty catalog leaves the existing Room data unchanged.
6. A successful refresh upserts managed entries. Managed entries omitted from a later authoritative document are retired locally rather than deleted.
7. The bundled seed uses conflict-ignore semantics and therefore cannot overwrite a row already refreshed from the server.

## Offline guarantee

Without a network connection, users must still be able to:

- open the app and switch among existing local vehicles;
- select catalog vehicles that were already stored locally;
- use custom vehicle entry;
- record, edit and view charging records;
- start, resume and finish trips;
- view statistics and vehicle state;
- use the last successfully cached Hero artwork when Coil has it available.

Catalog refresh never blocks `MainActivity`, `MainViewModel`, Room reads, or any business workflow.

## Catalog lifecycle

`catalogId` is a stable identity and must not be renamed after publication.

The admin action labelled **下架** sets `isActive=false`; it does not physically delete the catalog row. Retired entries disappear from new-vehicle selection but may remain locally so an existing `UserVehicle.catalogVehicleId` continues to resolve metadata. The admin can restore an entry later.

Most importantly, catalog updates never rewrite `VehicleEntity` values already copied into a user's vehicle. Battery capacity, range, brand/model edits and historical records remain user-owned snapshots unless the user explicitly edits their vehicle.

## Hero relationship

A catalog item may contain `heroArtworkKey`. The Hero UI observes this value from local Room by `catalogId`, then resolves the actual image URL through the independently versioned Hero manifest. This lets a newly managed vehicle receive a Hero without adding another hard-coded Android mapping.

If a catalog item has no Hero key or the image cannot be downloaded, the app falls back to its existing generic/legacy vehicle presentation rather than failing the vehicle workflow.
