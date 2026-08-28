# EV Charge Book v0.5 Hero Asset Delivery

## Goal

Keep the Dashboard Hero visually rich without packaging multi-megabyte vehicle artwork into every APK.

## Delivery model

```text
APK
  -> tiny Compose vehicle fallback
  -> remote Hero manifest
  -> versioned high-quality WebP
  -> Coil memory + disk cache
```

The app always has a local fallback. A first successful network load is cached on device and later launches reuse the disk cache.

## Remote manifest

Default endpoint:

```text
https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/hero-assets/manifest-v1.json
```

The endpoint can be overridden at build time with `HERO_ARTWORK_MANIFEST_URL`, so a CDN or first-party object store can replace GitHub Raw later without changing the runtime architecture.

Each artwork entry has a stable key, version, and HTTPS URL. Increment the version whenever the remote visual changes so the app gets a new Coil cache key without deleting the old cache globally.

## Repository layout

```text
hero-assets/
  manifest-v1.json
  remote/
    byd_seal_2025.webp
    leapmotor_c16_2026.webp
    tesla_model_3.webp
    xiaomi_su7_2024.webp
    xiaomi_su7_ultra_2024.webp
    xiaomi_yu7_2025.webp
```

Files under `hero-assets/remote` are network-delivery assets only. They are outside `android/app/src/main/res`, so Gradle does not package them into the APK.

## APK size effect

Before this change the six Hero WebP resources under `drawable-nodpi` totalled 11,116,294 bytes (about 10.6 MiB). They are removed from Android resources.

The packaged fallback is the existing Compose vehicle icon/gradient and therefore adds no per-vehicle bitmap weight.

## Caching and offline behavior

1. Resolve the selected vehicle to a stable Hero key.
2. Read the versioned manifest from GitHub Raw (or a build-time override).
3. Persist the last valid manifest in app preferences.
4. Load the resolved artwork with Coil.
5. Enable Coil memory cache and disk cache with a key containing `vehicleKey + version`.
6. If the manifest is unavailable, use the catalog's direct GitHub Raw URL for the known vehicle.
7. If no network/cached image is available, keep the lightweight Compose fallback visible.

No screen blocks waiting for artwork.

## Updating Hero artwork

A Hero image can change without rebuilding or releasing the APK:

1. replace the matching file under `hero-assets/remote/`;
2. increment that artwork's `version` in `manifest-v1.json`;
3. merge the asset change to `main`.

GitHub Raw then serves the new file and the version change creates a fresh Coil disk-cache key on the next manifest refresh.

`.github/workflows/hero-assets-publish.yml` is intentionally validation-only. It checks:

- manifest schema version;
- HTTPS GitHub Raw URLs;
- manifest/file consistency;
- non-empty artwork files;
- remote WebP hard ceiling of 2.5 MB per file.

A previous first-party-server publishing experiment was not retained because the current public web routing does not expose arbitrary Hero asset paths reliably. The runtime remains ready for a future CDN through `HERO_ARTWORK_MANIFEST_URL`.

## Local APK budget

Android CI rejects WebP files larger than 350 KB under `android/app/src/main/res/drawable-nodpi`.

Use remote Hero assets for photographic artwork. Keep only small icons or intentional lightweight fallback assets in APK resources.

## Recommended future artwork target

Remote artwork should normally be optimized to:

- 1200x900 or 1440x1080;
- WebP lossy;
- roughly 100-350 KB when visual quality allows.

The 2.5 MB workflow limit is a safety ceiling, not the target size.
