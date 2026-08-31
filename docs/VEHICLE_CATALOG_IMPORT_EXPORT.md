# Vehicle Catalog Import / Export Standard

Status: **Product / admin authority**  
Owner: Vehicle catalog maturity (`#244`)

## Purpose

Vehicle configuration must be maintainable in bulk. Manual Web Admin entry is a convenience path, not the only catalog maintenance path.

The Web Admin supports two portable configuration formats:

- **JSON** — complete catalog configuration transfer / backup
- **CSV** — spreadsheet-friendly bulk vehicle maintenance

Logo and Hero image binaries are intentionally excluded from both formats. Images remain managed assets and are uploaded separately through Web Admin.

## Import semantics

All imports use **merge / upsert** semantics:

- matching `brandId` updates the existing brand metadata
- matching `catalogId` updates the existing standard vehicle configuration
- new IDs create new records
- existing records not present in the import file are not deleted or retired automatically
- `isActive` in the import controls the imported record's active state
- stable IDs remain immutable after creation

This prevents a partial spreadsheet/import from accidentally deleting the production catalog.

## JSON format v1

```json
{
  "format": "ev-charge-book-vehicle-catalog",
  "version": 1,
  "exportedAt": "2026-08-31T00:00:00.000Z",
  "brands": [
    {
      "brandId": "leapmotor",
      "name": "零跑",
      "englishName": "Leapmotor",
      "isActive": true
    }
  ],
  "vehicles": [
    {
      "catalogId": "leap-c16-2026-reev-67",
      "brandId": "leapmotor",
      "series": "C16",
      "modelName": "C16 2026款",
      "modelYear": 2026,
      "trimName": "增程版",
      "powertrainType": "REEV",
      "batteryCapacityKwh": 67.7,
      "rangeKm": 520,
      "rangeStandard": "CLTC",
      "heroArtworkKey": "leapmotor-c16-2026",
      "isActive": true
    }
  ]
}
```

The JSON export does **not** include:

- Logo binary files
- Hero binary files
- Logo CDN URLs / versions
- user vehicles / nicknames / charging records / trips

`heroArtworkKey` is retained because it is configuration metadata. If the target environment does not yet have the referenced Hero image, upload/publish that image later through the Hero page.

## CSV format v1

CSV is intended for Excel / Numbers / spreadsheet editing.

Columns:

```text
brandId
brandName
brandEnglishName
catalogId
series
modelName
modelYear
trimName
powertrainType
batteryCapacityKwh
rangeKm
rangeStandard
heroArtworkKey
isActive
```

One CSV row represents one standard vehicle configuration. Brand values are repeated on each row; import de-duplicates brands by `brandId` and writes brands before vehicles.

Supported `isActive` values include:

```text
true / false
1 / 0
yes / no
是 / 否
```

## Hero key rule

`heroArtworkKey` is selected from existing managed Hero keys in the Web vehicle editor. It should normally be created/published from the **Hero 图片** page first.

A key may exist before its image is published. This allows configuration import to happen first and image upload later.

## Brand Logo rule

Logo belongs to `brandId`, not to `catalogId`.

```text
Brand
  -> Light Logo
  -> Dark Logo
  -> many vehicle configurations
```

All vehicles under the same brand automatically reuse the same managed brand Logo. Import/export never duplicates Logo image data per vehicle.

Logo files follow `docs/BRAND_LOGO_STANDARD.md` and are uploaded separately through the Brand Logo configuration center.
