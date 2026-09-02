# EV Charge Book Vehicle Resource Admin

Protected Web Admin for the managed vehicle catalog, brand Logos and vehicle Hero artwork.

## Product boundary

The Web Admin is the authority for **which brands and models are supported**. Android consumes a validated local Room cache and must not contain a supported-brand/model whitelist.

From the admin page an operator can:

- create/edit/retire brands;
- publish light-surface and dark-surface brand Logo variants;
- create/edit/retire standard vehicle models;
- maintain model year, trim, powertrain, battery, rated range/range standard and Hero key;
- publish and roll back Hero artwork.

Adding a new brand/model or replacing a Logo/Hero must not require an Android release.

## Brand Logo contract

The implementation follows `docs/BRAND_LOGO_STANDARD.md`:

- PNG/WebP source upload;
- normalized public artifact is exactly `512 x 512` transparent WebP;
- transparent bounds are trimmed before normalization;
- square/vertical marks are contained inside `384 x 384`;
- horizontal wordmarks are contained inside `416 x 192`;
- no crop/stretch;
- 256 KiB hard ceiling;
- immutable filename `brand_<brandId>_<variant>_vN.webp`;
- binary is written before the catalog pointer is updated;
- light/dark variants are versioned independently.

The operator is responsible for uploading an official brand asset with appropriate provenance. The service does not redraw, recolor or fabricate a trademark.

## Hero contract

The vehicle catalog stores one stable semantic **base Hero key** such as `leapmotor-c16-2026`. Theme variants belong in the Hero manifest, not in the catalog row:

```text
<base>-dark
<base>-light
```

For example:

```text
leapmotor-c16-2026-dark
leapmotor-c16-2026-light
```

Android resolves the variant from the **EV Charge Book in-app theme**, which may differ from the device/system theme. Dark mode prefers `<base>-dark`; light mode prefers `<base>-light`. Legacy `<base>` entries remain a compatibility fallback while an older asset set is being migrated.

A newly published supported Hero should provide **both** light and dark variants. Publishing only the base key is legacy-compatible but does not constitute complete light/dark visual acceptance.

Both variants must preserve the Hero safe-area contract: the top status/title zone remains contrast-safe for white system/title chrome, while the light variant uses a brighter vehicle/background treatment and a light lower transition into the dashboard surface.

Hero publishing:

- PNG/WebP source;
- landscape ratio `1.40:1` to `1.55:1`;
- minimum `1200 x 800`;
- normalized to real `1600 x 1100` WebP;
- 2.5 MiB hard ceiling;
- immutable `_vN.webp` output;
- previous manifest/version remains available for rollback.

## Runtime files

The service uses:

```text
/data/release-meta/vehicle-catalog-v1.json
/data/release-meta/hero-assets-v1.json
/data/releases/brand-logos/*.webp
/data/releases/hero-assets/*.webp
```

`vehicle-catalog-v1.json` contains root-level `brands` plus `vehicles`. Vehicles reference brands using stable `brandId`; the duplicated vehicle `brand` text remains a compatibility snapshot for older clients.

If an older server catalog has no `brands`, the admin performs a compatibility upgrade in memory and persists the normalized structure on the next catalog mutation.

## Environment

```bash
mkdir -p /opt/ev-charge-book
cat > /opt/ev-charge-book/hero-admin.env <<'EOF'
HERO_ADMIN_USER=admin
HERO_ADMIN_PASSWORD=CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD
HERO_PUBLIC_ORIGIN=https://groupim.cn
HERO_PUBLIC_ASSET_BASE=https://groupim.cn/ev-charge-book/releases/hero-assets
BRAND_LOGO_PUBLIC_ASSET_BASE=https://groupim.cn/ev-charge-book/releases/brand-logos
EOF
chmod 600 /opt/ev-charge-book/hero-admin.env
```

Optional seed overrides:

```text
HERO_MANIFEST_SEED_URL
VEHICLE_CATALOG_SEED_URL
HERO_RELEASE_ROOT
HERO_META_ROOT
```

## Docker deployment

Recommended source path:

```text
/opt/ev-charge-book/hero-admin-src
```

Build:

```bash
cd /opt/ev-charge-book/hero-admin-src
docker build -t ev-charge-book-hero-admin:local .
```

Compose service:

```yaml
hero-admin:
  image: ev-charge-book-hero-admin:local
  container_name: ev-charge-book-hero-admin
  env_file:
    - /opt/ev-charge-book/hero-admin.env
  volumes:
    - /opt/ev-charge-book/releases:/data/releases
    - /opt/ev-charge-book/release-meta:/data/release-meta
  networks:
    - app-network
  restart: unless-stopped
```

No public container port is required; Nginx reaches `hero-admin:8080` on the existing Docker network.

## Nginx

The existing `/ev-charge-book/releases/` static location serves both `hero-assets/` and `brand-logos` immutable files.

The mutable runtime pointers must not be cached as immutable:

```nginx
location = /ev-charge-book/release-meta/hero-assets-v1.json {
    alias /opt/ev-charge-book/release-meta/hero-assets-v1.json;
    default_type application/json;
    add_header Cache-Control "no-store, no-cache, must-revalidate, max-age=0" always;
    expires -1;
}

location = /ev-charge-book/release-meta/vehicle-catalog-v1.json {
    alias /opt/ev-charge-book/release-meta/vehicle-catalog-v1.json;
    default_type application/json;
    add_header Cache-Control "no-store, no-cache, must-revalidate, max-age=0" always;
    expires -1;
}

location = /ev-charge-book/hero-admin {
    return 301 /ev-charge-book/hero-admin/;
}

location ^~ /ev-charge-book/hero-admin/ {
    client_max_body_size 12m;
    proxy_pass http://hero-admin:8080/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header Authorization $http_authorization;
    proxy_connect_timeout 10s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
    add_header Cache-Control "no-store" always;
}
```

Validate and reload:

```bash
docker exec nginx nginx -t
docker compose up -d hero-admin
docker compose restart nginx
```

## Verification

Without credentials the admin should return `401`:

```bash
curl -I https://groupim.cn/ev-charge-book/hero-admin/
```

Public runtime documents should return successfully:

```bash
curl https://groupim.cn/ev-charge-book/release-meta/vehicle-catalog-v1.json
curl https://groupim.cn/ev-charge-book/release-meta/hero-assets-v1.json
```

After publishing a Logo, verify its immutable URL under:

```text
https://groupim.cn/ev-charge-book/releases/brand-logos/
```

Android keeps the last valid Room catalog and Coil disk cache, so catalog/Logo network failures must not erase existing offline data.
