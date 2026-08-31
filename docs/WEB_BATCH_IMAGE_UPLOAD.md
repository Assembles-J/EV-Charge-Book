# Web Admin batch image upload

EV Charge Book batch image maintenance is owned by the **Resource Workbench** (`hero-admin/static/resource-workbench.html`).

The previous split Brand Logo / Hero batch panels are retained only as legacy implementation code and are no longer exposed in the main admin UI. Single-image Brand/Hero maintenance remains available on the normal admin tabs.

For the coordinated bundle protocol, duplicate Hero confirmation and Light/Dark Hero semantics, also see `docs/RESOURCE_BUNDLE_WORKFLOW.md`.

## One upload entrance

Logo and Hero images may be selected or dragged in **together**.

Recommended discoverable source names:

```text
brand_<brandId>_light.webp
brand_<brandId>_dark.webp
hero_<heroKey>_dark.webp
hero_<heroKey>_light.webp
```

Generation/version notes may follow the managed prefix, for example `_v2`, `_final` or a date.

The browser normalizes the basename (lowercase, `_`/spaces -> `-`) and uses longest-prefix matching when it can.

## Automatic matching is a suggestion

Automatic matching is not a publish requirement.

Every selected image remains an editable queue row. The operator can change:

- resource type: Brand Logo / Vehicle Hero;
- Brand target;
- Hero semantic Key target;
- a new manual Hero Key;
- Light / Dark variant;
- Logo can intentionally target both Light + Dark when the same official asset has sufficient contrast on both surfaces.

Therefore an unmatched image is **not discarded**. It is blocked from publishing until the operator explicitly assigns a valid target.

## Standard published filename

The original upload filename is only an input hint. It is never the authoritative server filename.

After target/variant selection, Web Admin previews the filename that the server is expected to publish.

Examples:

```text
brand_leapmotor_dark_final.webp
-> brand_leapmotor_dark_v4.webp

hero_byd-seal-08-2026_light_generated-3.webp
-> byd_seal_08_2026_light_v2.webp
```

The server remains authoritative for the actual `vN` value and never overwrites an existing immutable release.

## Priority

Within one queue, the same logical target + variant can be claimed once.

If several files resolve to the same target + variant, the earliest file keeps priority. Later rows are visibly blocked until they are manually reassigned or removed from the next selection.

This applies independently to:

```text
Logo brand + Light
Logo brand + Dark
Hero semantic key + Light
Hero semantic key + Dark
```

## Brand Logo validation

Brand Logo follows `docs/BRAND_LOGO_STANDARD.md`.

Browser preflight checks include:

- PNG / WebP input;
- usable source resolution;
- visible Logo pixels;
- transparent background;
- contrast against the selected Light/Dark surface.

A Logo may be reused for both interface variants only when it passes contrast requirements on both surfaces.

One published Brand Logo remains shared by every vehicle under the same `brandId`.

## Hero validation

Hero preflight checks include:

- PNG / WebP input;
- at least 1200x800 source resolution;
- aspect ratio in the accepted 1.40-1.55:1 range.

Server publication still owns the final 1600x1100 WebP conversion, immutable versioning and size budget.

A vehicle catalog row stores one semantic `heroArtworkKey`. Workbench Hero uploads publish the derived keys:

```text
<heroArtworkKey>-dark
<heroArtworkKey>-light
```

Existing legacy `<heroArtworkKey>` assets remain a runtime fallback.

## Existing Hero Key safety

When the selected Hero semantic key already exists, or is referenced by another catalog vehicle, Web Admin must show the current state and require explicit update confirmation.

The next publish is a new immutable version; it is never a silent overwrite.

## Transport safety

Files are still sent sequentially through the existing authenticated upload endpoints rather than one giant multipart request. This preserves server validation and avoids concurrent manifest/catalog write races.
