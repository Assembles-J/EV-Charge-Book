# EV Charge Book Hero Admin

A deliberately small web publisher for **existing vehicle Hero artwork**.

Daily workflow after one-time deployment:

1. Open `https://groupim.cn/ev-charge-book/hero-admin/`.
2. Sign in with HTTP Basic credentials configured in `HERO_ADMIN_USER` / `HERO_ADMIN_PASSWORD`.
3. Select an existing artwork key.
4. Drop a PNG or WebP Hero image.
5. Click **发布 Hero**.

The service then:

- accepts PNG / WebP only;
- requires a landscape source close to the current Dashboard contract (`1.40:1` to `1.55:1`, recommended `1600 x 1100`);
- requires at least `1200 x 800` source pixels;
- converts the source to a real `1600 x 1100` WebP;
- starts at quality 88 and only lowers quality when required by the 2.5 MiB hard ceiling;
- creates an immutable filename such as `leapmotor_c16_2026_v2.webp`;
- writes the image before switching the manifest pointer;
- increments the selected artwork version automatically;
- atomically updates `/opt/ev-charge-book/release-meta/hero-assets-v1.json`;
- keeps the previous manifest as `hero-assets-v1.json.bak`;
- never writes Hero image binaries to Git.

The v1 admin intentionally does **not** create new Android vehicle mappings. Adding a brand-new vehicle still needs one Android/catalog change. Later artwork replacements are admin-page only.

## One-time server setup

### 1. Create the admin password file

Do not commit this file.

```bash
mkdir -p /opt/ev-charge-book
cat > /opt/ev-charge-book/hero-admin.env <<'EOF'
HERO_ADMIN_USER=admin
HERO_ADMIN_PASSWORD=CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD
HERO_PUBLIC_ORIGIN=https://groupim.cn
EOF
chmod 600 /opt/ev-charge-book/hero-admin.env
```

### 2. Put this `hero-admin/` directory on the server

Recommended path:

```text
/opt/ev-charge-book/hero-admin-src
```

Build it once:

```bash
cd /opt/ev-charge-book/hero-admin-src
docker build -t ev-charge-book-hero-admin:local .
```

### 3. Add the service to the existing `/opt/app/docker-compose.yml`

The current production compose already has `app-network` and mounts `/opt/ev-charge-book` into Nginx. Add:

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

No public container port is required. Nginx reaches the service over the existing Docker network.

### 4. Add two Nginx locations inside the existing `groupim.cn` HTTPS server

The existing `/ev-charge-book/releases/` location can stay as-is. Add:

```nginx
# Public runtime Hero manifest. Mutable pointer: never cache it as immutable.
location = /ev-charge-book/release-meta/hero-assets-v1.json {
    alias /opt/ev-charge-book/release-meta/hero-assets-v1.json;
    default_type application/json;

    add_header Cache-Control "no-store, no-cache, must-revalidate, max-age=0" always;
    add_header Pragma "no-cache" always;
    expires -1;
}

location = /ev-charge-book/hero-admin {
    return 301 /ev-charge-book/hero-admin/;
}

# Protected application. Authentication is enforced by the Hero Admin service itself.
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

### 5. Verify

```bash
curl -I https://groupim.cn/ev-charge-book/release-meta/hero-assets-v1.json
curl https://groupim.cn/ev-charge-book/hero-admin/ -I
```

The admin URL should return `401` without credentials. That is expected.

The first time the server manifest is missing, the service seeds it from the small GitHub `hero-assets/manifest-v1.json`. This lets existing artwork continue working while individual vehicles are migrated to first-party CDN URLs through the admin page.

## Android runtime follow-up

This PR deliberately does **not** switch Android to the first-party manifest yet. Keep the current GitHub Raw manifest endpoint until the server setup above is deployed and verified.

After both checks are green:

```text
https://groupim.cn/ev-charge-book/release-meta/hero-assets-v1.json
https://groupim.cn/ev-charge-book/hero-admin/
```

make a separate one-line Android PR changing the default `HERO_ARTWORK_MANIFEST_URL` to the first-party manifest. This keeps deployment order explicit and prevents a partially configured server from becoming a runtime dependency.
