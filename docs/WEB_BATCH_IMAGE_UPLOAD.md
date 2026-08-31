# Web Admin batch image upload

EV Charge Book Web Admin supports multi-file selection and drag-and-drop for Brand Logo and Hero artwork.

## Matching rule

The browser normalizes the file basename (lowercase, `_`/spaces -> `-`) and matches it against existing managed IDs by **longest filename prefix**.

Examples:

- `xiaomi-su7_v2.webp` -> Hero Key `xiaomi-su7`
- `xiaomi-su7-ultra_20260831.webp` -> Hero Key `xiaomi-su7-ultra` (longest prefix wins)
- `brand_xiaomi_v3.webp` -> Brand `xiaomi` when the brand has `logoKey=brand-xiaomi`
- `xiaomi_final.png` -> Brand `xiaomi`

Text after the managed prefix is treated only as generation/version/maintenance context. It does not change the target.

## Priority

Within one selected/dropped batch, the browser preserves file order. If multiple files match the same target variant, the **first file in the batch wins** and later duplicates are skipped visibly before publishing.

Existing server assets do not block a new batch publish. A successful publish still uses the existing immutable versioning rules and creates the next server version.

## Brand Logo

Brand Logo batch upload uses **one** multi-file drop zone. Operators do not pre-sort AI-generated images into Light / Dark upload entrances.

For every matched Logo, the browser validates the image before publish:

- PNG / WebP only;
- minimum usable image size;
- enough visible Logo pixels;
- transparent background must be present;
- contrast against both the light admin/app surface and the dark surface is measured from the visible Logo pixels.

The result is assigned automatically:

- dark Logo -> `light` interface asset;
- light Logo -> `dark` interface asset;
- a mid-tone/brand-color Logo with sufficient contrast on both surfaces -> reused for both variants;
- an opaque or low-contrast image -> validation failure and is not published.

When several files in the same batch can fill the same brand + interface variant, the earliest file keeps that slot. A later file may still fill the other variant if the earlier image was only suitable for one surface.

Each file is still matched by `brandId`, `logoKey`, or `brand-<brandId>` prefix. One Brand Logo continues to be reused by all models under the same `brandId`.

The single-brand editor still shows separate light/dark preview cards because they are useful for final visual inspection and targeted replacement. Those cards are not required for batch classification.

## Hero

Hero batch upload matches only existing managed Hero Keys collected from the Hero manifest and vehicle catalog references. It does not silently create a new Hero Key from an arbitrary filename.

Files are uploaded sequentially so manifest/catalog writes remain atomic and deterministic. Each file still passes the normal server-side Hero validation and 1600x1100 WebP conversion pipeline.

## Safety

- Unmatched files are skipped.
- Duplicate target variants are skipped after the first compatible file.
- Brand Logo validation happens before upload; invalid transparency/contrast does not reach the publish endpoint.
- Batch publishing reuses the existing authenticated versioned upload endpoints.
- The browser sends files one by one instead of combining them into one large multipart request.
