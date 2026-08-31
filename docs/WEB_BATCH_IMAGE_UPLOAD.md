# Web Admin batch image upload

EV Charge Book Web Admin supports multi-file selection and drag-and-drop for Brand Logo and Hero artwork.

## Matching rule

The browser normalizes the file basename (lowercase, `_`/spaces -> `-`) and matches it against existing managed IDs by **longest filename prefix**.

Examples:

- `xiaomi-su7_v2.webp` -> Hero Key `xiaomi-su7`
- `xiaomi-su7-ultra_20260831.webp` -> Hero Key `xiaomi-su7-ultra` (longest prefix wins)
- `brand_xiaomi_dark_v3.webp` -> Brand `xiaomi` when the brand has `logoKey=brand-xiaomi`
- `xiaomi_final.png` -> Brand `xiaomi`

Text after the managed prefix is treated only as generation/version/maintenance context. It does not change the target.

## Priority

Within one selected/dropped batch, the browser preserves file order. If multiple files match the same target, the **first file in the batch wins** and later duplicates are skipped visibly before publishing.

Existing server assets do not block a new batch publish. A successful publish still uses the existing immutable versioning rules and creates the next server version.

## Brand Logo

Brand Logo uses two explicit batch drop zones:

- Light Logo
- Dark Logo

This keeps visual variants unambiguous. Each selected file is matched by `brandId`, `logoKey`, or `brand-<brandId>` prefix. One Brand Logo continues to be reused by all models under the same `brandId`.

## Hero

Hero batch upload matches only existing managed Hero Keys collected from the Hero manifest and vehicle catalog references. It does not silently create a new Hero Key from an arbitrary filename.

Files are uploaded sequentially so manifest/catalog writes remain atomic and deterministic. Each file still passes the normal server-side Hero validation and 1600x1100 WebP conversion pipeline.

## Safety

- PNG / WebP only.
- Unmatched files are skipped.
- Duplicate matches are skipped after the first file.
- Batch publishing reuses the existing authenticated versioned upload endpoints.
- The browser sends files one by one instead of combining them into one large multipart request.
