# Brand Logo Asset Standard

Status: **Product / runtime authority**  
Owner: Vehicle catalog maturity (`#244`)

## 1. Purpose

Brand logos are managed catalog assets. Android must not package or hard-code a supported-brand logo map.

A new brand must become available through Web Admin + catalog refresh without an APK release.

## 2. Runtime contract

Every published runtime logo variant must be:

- **Format:** WebP with alpha transparency
- **Canvas:** exactly **512 × 512 px**
- **Background:** fully transparent; no baked card, circle, shadow, gradient or UI chrome
- **Color space:** sRGB
- **Target size:** normally **20–100 KB**
- **Soft ceiling:** **160 KB**
- **Hard ceiling:** **256 KiB** per variant
- **Minimum raster source:** 512 × 512 effective resolution before normalization
- **Resize behavior:** contain / letterbox into the transparent canvas; **never crop the logo**

The source upload may be PNG or WebP in v1. Vector originals should be retained by the asset owner when available, but the public Android runtime artifact remains normalized WebP until SVG runtime support is intentionally introduced.

## 3. Safe area / visual box

For compact vehicle selectors the important logo geometry must remain inside the center safe box:

- square/round/vertical marks: maximum visual bounds **384 × 384 px**
- minimum transparent padding: **64 px** on every side when the mark shape allows it
- horizontal wordmark-only brands: maximum visual bounds **416 × 192 px**, centered on the 512 × 512 canvas

Do not enlarge a logo to touch the canvas edge. Different brands should have approximately equal **optical weight**, not equal raw bounding-box size.

## 4. Allowed content

Use the official brand mark or official wordmark only.

Not allowed:

- dealer logos
- vehicle-model badges (`C16`, `SU7`, `Model Y`, etc.) as the brand logo
- marketing slogans
- screenshots
- watermarks
- manually redrawn / approximated trademark shapes
- extra text outside the official logo artwork
- rounded-square app-icon backgrounds unless that background is part of the official brand identity

Asset provenance / source should be recorded in admin metadata when practical.

## 5. Light and dark variants

Catalog brands support two semantic variants:

```json
{
  "brandId": "leapmotor",
  "name": "零跑",
  "logoKey": "brand-leapmotor",
  "logoLightKey": "brand-leapmotor-light",
  "logoDarkKey": "brand-leapmotor-dark",
  "isActive": true
}
```

Meaning:

- `logoLightKey`: artwork intended to be readable **on a light UI surface**
- `logoDarkKey`: artwork intended to be readable **on a dark UI surface**
- if the same official full-color artwork works on both surfaces, both may resolve to the same published asset
- Android may fall back to `logoKey`, then to a generic brand placeholder

The app must never recolor arbitrary multi-color brand artwork by itself.

## 6. Publishing and caching

Published files use immutable versioned names:

```text
brand_<brandId>_<variant>_v<N>.webp
```

Example:

```text
brand_leapmotor_dark_v3.webp
```

The mutable manifest/catalog points to the latest immutable URL and version. Publishing a replacement creates a new file/version rather than overwriting the old binary.

Android behavior:

1. UI reads brand metadata from the local Room catalog.
2. Image loader uses memory + disk cache.
3. The asset version is part of the cache key.
4. Network failure keeps the last successful cached logo.
5. No cached logo / first offline launch falls back to a generic vehicle-brand placeholder.
6. Logo failure must never make the vehicle/catalog row unusable.

## 7. Web Admin validation

The brand Logo publisher must reject or normalize uploads that violate the runtime contract.

Required checks:

- PNG / WebP only in v1
- decodable image
- alpha-capable output
- source large enough for a clean 512 × 512 result
- preserve aspect ratio
- contain without crop
- center the visual artwork
- output real 512 × 512 WebP
- enforce 256 KiB hard limit
- immutable versioned filename
- write binary before switching catalog/manifest pointer
- keep previous version available for rollback

## 8. UI usage

### Vehicle switcher

Preferred representation:

```text
[brand logo]  nickname
              fallback model/series when nickname is blank
```

Logo display box: **32–40 dp**. Do not show battery/range in the compact switcher.

### Vehicle catalog picker

Logo display box: **40–48 dp**, followed by brand + model / trim facts.

### Vehicle detail

Logo may be larger, but the same managed asset/key is used. Standard vehicle specifications remain catalog-owned and read-only.

## 9. Ownership boundary

```text
Web Admin
  -> Brand metadata
  -> Brand Logo assets
  -> Vehicle catalog
  -> versioned publish

Android
  -> validate catalog
  -> Room last-known-good cache
  -> image disk cache
  -> render only
```

Adding or replacing a brand logo must not require editing Kotlin source or releasing a new APK.
