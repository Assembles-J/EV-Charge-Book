# EV Charge Book Vehicle Resource Bundle Workflow

Status: **Product / admin authority**  
Owner: Vehicle catalog maturity (`#244`)

## Why this exists

A supported vehicle is not only one catalog row. Normal onboarding may require a coordinated set of metadata and images:

```text
Brand metadata
  + Brand Logo Light
  + Brand Logo Dark
  + Vehicle catalog row
  + Hero Dark
  + Hero Light
```

The Web Admin therefore provides two maintenance modes:

- **single-item maintenance** — existing Brand / Vehicle / Hero screens and the single Logo/Hero prompt library;
- **resource bundle onboarding** — one workbench and one all-in-one prompt for adding or intentionally updating a complete vehicle resource set.

The single-item tools remain useful for replacing one Logo, correcting one catalog field, or publishing one Hero revision. The resource workbench is the preferred path for new vehicle onboarding.

## Resource bundle JSON v1

The workbench accepts this machine-readable envelope:

```json
{
  "format": "ev-charge-book-resource-bundle",
  "version": 1,
  "heroKey": "byd-seal-08-2026",
  "updateIntent": false,
  "changeSummary": ["new resource bundle"],
  "needsSource": [],
  "catalogImport": {
    "format": "ev-charge-book-vehicle-catalog",
    "version": 1,
    "brands": [
      {
        "brandId": "byd",
        "name": "比亚迪",
        "englishName": "BYD",
        "isActive": true
      }
    ],
    "vehicles": [
      {
        "catalogId": "example-catalog-id",
        "brandId": "byd",
        "series": "example-series",
        "modelName": "example model",
        "modelYear": null,
        "trimName": null,
        "powertrainType": "BEV",
        "batteryCapacityKwh": null,
        "rangeKm": null,
        "rangeStandard": null,
        "heroArtworkKey": "byd-seal-08-2026",
        "isActive": true
      }
    ]
  },
  "assets": {
    "brandLogo": {
      "light": "brand_byd_light.webp",
      "dark": "brand_byd_dark.webp"
    },
    "hero": {
      "dark": "hero_byd-seal-08-2026_dark.webp",
      "light": "hero_byd-seal-08-2026_light.webp"
    }
  }
}
```

The example identifiers above demonstrate format only. Catalog facts must not be invented when onboarding a real vehicle.

The workbench intentionally accepts exactly **one brand + one vehicle** per resource bundle. Bulk spreadsheet/catalog maintenance remains owned by `docs/VEHICLE_CATALOG_IMPORT_EXPORT.md`.

## Hero semantic key and variants

`VehicleCatalog.heroArtworkKey` stores the stable **base semantic key** only:

```text
byd-seal-08-2026
```

It does not contain a theme variant.

Published Hero assets derive two manifest keys:

```text
<heroArtworkKey>-dark
<heroArtworkKey>-light
```

For example:

```text
byd-seal-08-2026-dark
byd-seal-08-2026-light
```

This keeps the catalog stable while allowing the Android UI to select an environment-appropriate image.

### Android resolution order

Dark UI:

```text
<base>-dark
-> legacy <base>
```

Light UI:

```text
<base>-light
-> <base>-dark
-> legacy <base>
```

The legacy base fallback is mandatory so existing published Hero assets remain compatible during migration.

## Image source filenames versus published filenames

AI/image-generation source files should use discoverable names:

```text
brand_<brandId>_light.webp
brand_<brandId>_dark.webp
hero_<heroKey>_dark.webp
hero_<heroKey>_light.webp
```

Additional generation notes/version suffixes after the managed prefix are allowed.

These source filenames are **not** authoritative release filenames. Web Admin validates the target and writes an immutable versioned filename on publish.

Examples:

```text
brand_byd_dark.webp
-> brand_byd_dark_v3.webp

hero_byd-seal-08-2026_light_final.webp
-> byd_seal_08_2026_light_v2.webp
```

The server owns the final name and version. Users must not manually maintain the `vN` suffix.

## Unified image upload

The Resource Workbench accepts Logo and Hero images in one multi-file drop.

The browser tries automatic classification and longest-prefix matching first, but **automatic matching is only a suggestion**.

Every row remains editable before publish:

- resource type: Brand Logo / Vehicle Hero;
- target: managed brand or Hero semantic key;
- Hero target may be entered manually when creating a new semantic key;
- variant: Light / Dark, or both for a reusable Logo.

Therefore an unmatched source file is not discarded. The operator can manually assign it to the correct managed target.

Before upload the workbench previews the expected server-standard filename.

## Validation

Brand Logo validation remains aligned with `docs/BRAND_LOGO_STANDARD.md`:

- PNG / WebP input;
- usable source resolution;
- transparent background;
- visible artwork pixels;
- sufficient contrast for the selected Light/Dark surface.

Hero preflight requires:

- PNG / WebP input;
- at least 1200x800 source resolution;
- approximately 1.45:1 aspect ratio (accepted range 1.40-1.55);
- the final server pipeline still converts to 1600x1100 WebP and applies the authoritative size budget.

## Duplicate and update confirmation

A stable Hero semantic key may be shared by multiple trim rows intentionally. Updating it therefore can affect more than one vehicle.

The workbench must never silently overwrite/repoint an existing semantic Hero asset.

When a base Hero Key already has any of these:

```text
<base>
<base>-dark
<base>-light
```

or when another catalog row references the same base key, the workbench shows the existing state and requires explicit confirmation before publishing the next immutable version.

The same review applies to an existing `brandId` or `catalogId`: changed fields are shown before the operator confirms an update.

Within one image queue, the same target + variant may only be claimed once. The first file keeps priority; later duplicates are blocked until manually reassigned.

## Full prompt versus single prompt

`hero-admin/static/prompt-library.html` remains the single-item copy center.

`hero-admin/static/asset-bundle-prompt.html` is the full onboarding prompt. It dynamically reads the current Logo standard and current Dark Hero prompt from the single-item prompt library instead of maintaining copied text.

The full prompt requires an AI-assisted onboarding result to return:

1. stable `brandId`, `catalogId`, and base `heroKey`;
2. one-row vehicle catalog import JSON;
3. Logo Light and Logo Dark requirements + source filenames;
4. Hero Dark and Hero Light requirements + source filenames;
5. one machine-readable `ev-charge-book-resource-bundle` JSON block for the workbench;
6. no invented standard vehicle facts; unknown optional facts use `null` and are listed under `needsSource`.

For create mode, the prompt is supplied with current Hero semantic keys and explicitly forbids silent collision. For update mode, an existing key must be intentional and change points must be listed.
