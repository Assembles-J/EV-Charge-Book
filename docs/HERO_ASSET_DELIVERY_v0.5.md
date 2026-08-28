# EV Charge Book v0.5 Hero Asset Delivery

## Goal

Keep the Dashboard Hero visually rich without packaging or versioning multi-megabyte vehicle artwork inside the Android app or Git repository.

## Delivery model

```text
APK
  -> tiny Compose vehicle fallback
  -> GitHub Raw manifest (small configuration only)
  -> groupim.cn Hero WebP
  -> Coil memory + disk cache
```

The manifest intentionally remains in Git because it is tiny, reviewable configuration. Full-size Hero WebP files are CDN-only and must not be committed to the repository.

## Runtime manifest

Default endpoint:

```text
https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/hero-assets/manifest-v1.json
```

The endpoint can still be overridden at build time with `HERO_ARTWORK_MANIFEST_URL`.

Each artwork entry has:

- a stable artwork key;
- an integer `version`;
- an HTTPS URL under `https://groupim.cn/ev-charge-book/releases/hero-assets/`.

The version is part of the Coil cache key. Increment it whenever the visual changes.

## Repository boundary

The repository keeps only configuration and code:

```text
hero-assets/
  manifest-v1.json
```

Do not commit `*.webp` Hero artwork under `hero-assets/`. `.gitignore` and Hero CI both enforce this rule.

The old Hero binaries remain in historical Git commits, but they are removed from the current tree. Rewriting repository history is intentionally avoided because the historical size is small enough that a destructive history rewrite is not justified.

## APK and memory behavior

Hero artwork is not under `android/app/src/main/res`, so Gradle does not package it into the APK.

At runtime Coil downloads only artwork that is actually requested and uses memory/disk caching. The whole vehicle catalog is not decoded into RAM at once. A decoded image can use more RAM than its compressed WebP file size, which is another reason to keep Hero dimensions bounded.

## Approved Hero artwork target

The current Dashboard image slot is approximately `1.46:1`, so Hero artwork should target the same landscape composition instead of portrait artwork that would be heavily cropped.

Recommended production target:

- aspect ratio: approximately `1.45:1` / `16:11`;
- preferred size: `1600 x 1100` px;
- acceptable alternatives: `1440 x 990` or `1920 x 1320`;
- format: WebP, sRGB;
- target compressed size: roughly `400 KB - 1.2 MB` when quality allows;
- hard CI ceiling: `2.5 MiB`;
- no UI text baked into the image;
- leave a calm upper-left area for vehicle title content;
- keep the vehicle in the middle/lower visual region;
- preserve a relatively calm lower region for the glass state panel;
- use restrained dark EV atmosphere, metallic body rendering, and subtle reflection rather than excessive neon effects.

## Updating an existing vehicle Hero

Upload first, publish the manifest second.

Example: updating Leapmotor C16 from v1 to v2.

1. Produce the new Hero WebP using the approved landscape composition.
2. Prefer a new immutable CDN filename such as:

```text
leapmotor_c16_2026_v2.webp
```

3. Upload it to:

```text
https://groupim.cn/ev-charge-book/releases/hero-assets/leapmotor_c16_2026_v2.webp
```

4. Confirm the public URL is readable.
5. Update only the matching manifest entry:

```json
"leapmotor-c16-2026": {
  "version": 2,
  "url": "https://groupim.cn/ev-charge-book/releases/hero-assets/leapmotor_c16_2026_v2.webp"
}
```

6. Open/merge the manifest PR after Hero CI succeeds.

No Android release is required for an artwork-only update.

Do not overwrite the old CDN object when practical. Keeping v1/v2 files makes rollback a one-line manifest change and avoids intermediary CDN caches returning stale bytes.

## Adding a new vehicle Hero

A new vehicle requires one initial Android mapping change because the app must know which vehicle resolves to which stable artwork key.

1. Choose a permanent artwork key, for example `nio-et5t-2026`.
2. Upload `nio_et5t_2026_v1.webp` to the Hero CDN.
3. Add the manifest entry with `version: 1`.
4. Add the vehicle -> artwork-key mapping in `OfficialVehicleImageCatalog` (and the vehicle catalog itself if the model is new there).
5. Release that Android mapping once.

After that first mapping release, later visual replacements for the same vehicle are manifest/CDN-only and do not require a new APK.

## CI contract

`.github/workflows/hero-assets-publish.yml` is validation-only. It does not store or publish image bytes from Git.

It enforces:

- `schemaVersion == 1`;
- positive artwork versions;
- production `groupim.cn` Hero URLs;
- WebP URL suffixes;
- no duplicate artwork URLs;
- no Hero WebP files committed under `hero-assets/`;
- every referenced CDN object is publicly downloadable;
- downloaded bytes have a WebP RIFF header;
- each remote image is at most `2.5 MiB`.

This ordering ensures a manifest cannot safely merge before its referenced CDN files exist.

## Offline behavior

1. Resolve the selected vehicle to a stable Hero key.
2. Read the small versioned manifest from GitHub Raw.
3. Persist the last valid manifest in app preferences.
4. Load the CDN artwork with Coil memory and disk cache using `vehicleKey + version`.
5. If the manifest is unavailable, the catalog tries the direct first-party CDN URL for known vehicles.
6. If neither remote nor cached artwork is available, keep the lightweight Compose fallback visible.

No screen should block waiting for artwork.
