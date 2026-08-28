#!/usr/bin/env bash
set -euo pipefail

ROOT="/opt/ev-charge-book"
STAGING_ROOT="${ROOT}/hero-admin-deploy"
APP_ROOT="/opt/app"
NGINX_CONF="${APP_ROOT}/nginx.conf"
NGINX_CONTAINER="nginx"
NETWORK="${HERO_ADMIN_DOCKER_NETWORK:-app_gateway}"
IMAGE="ev-charge-book-hero-admin:local"
IMAGE_ARCHIVE="${STAGING_ROOT}/hero-admin-image.tar.gz"
CONTAINER="ev-charge-book-hero-admin"
ENV_FILE="${ROOT}/hero-admin.env"
MANIFEST="${ROOT}/release-meta/hero-assets-v1.json"
SEED_MANIFEST="${STAGING_ROOT}/hero-assets/manifest-v1.json"
CATALOG="${ROOT}/release-meta/vehicle-catalog-v1.json"
SEED_CATALOG="${STAGING_ROOT}/vehicle-catalog/catalog-v1.json"

log() {
  printf '[hero-admin-deploy] %s\n' "$*"
}

require_file() {
  if [ ! -f "$1" ]; then
    echo "Required file missing: $1" >&2
    exit 1
  fi
}

require_file "${IMAGE_ARCHIVE}"
require_file "${SEED_MANIFEST}"
require_file "${SEED_CATALOG}"
require_file "${NGINX_CONF}"

docker network inspect "${NETWORK}" >/dev/null

mkdir -p "${ROOT}/releases/hero-assets" "${ROOT}/release-meta"
chmod 755 "${ROOT}/releases" "${ROOT}/releases/hero-assets" "${ROOT}/release-meta"

if [ ! -f "${ENV_FILE}" ]; then
  log "Creating first-run Hero Admin credentials"
  umask 077
  password="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
  cat > "${ENV_FILE}" <<EOF
HERO_ADMIN_USER=admin
HERO_ADMIN_PASSWORD=${password}
HERO_PUBLIC_ORIGIN=https://groupim.cn
HERO_PUBLIC_ASSET_BASE=https://groupim.cn/ev-charge-book/releases/hero-assets
EOF
fi
chmod 600 "${ENV_FILE}"

if [ ! -f "${MANIFEST}" ]; then
  log "Seeding runtime Hero manifest"
  cp "${SEED_MANIFEST}" "${MANIFEST}"
  chmod 644 "${MANIFEST}"
fi

if [ ! -f "${CATALOG}" ]; then
  log "Seeding runtime vehicle catalog"
  cp "${SEED_CATALOG}" "${CATALOG}"
  chmod 644 "${CATALOG}"
fi

log "Loading prebuilt Hero Admin image"
gzip -t "${IMAGE_ARCHIVE}"
gzip -dc "${IMAGE_ARCHIVE}" | docker load >/dev/null

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
  echo "Expected image tag was not loaded: ${IMAGE}" >&2
  exit 1
fi

log "Starting Hero Admin container on ${NETWORK}"
docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
docker run -d \
  --name "${CONTAINER}" \
  --restart unless-stopped \
  --network "${NETWORK}" \
  --env-file "${ENV_FILE}" \
  -v "${ROOT}/releases:/data/releases" \
  -v "${ROOT}/release-meta:/data/release-meta" \
  "${IMAGE}" >/dev/null

healthy=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  if docker exec "${CONTAINER}" python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/healthz', timeout=2).read()" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done

if [ "${healthy}" -ne 1 ]; then
  docker logs "${CONTAINER}" || true
  echo "Hero Admin container did not become healthy" >&2
  exit 1
fi

HERO_MARKER="# BEGIN EV CHARGE BOOK HERO ADMIN"
if ! grep -Fq "${HERO_MARKER}" "${NGINX_CONF}"; then
  log "Installing Nginx Hero Admin routes"
  backup="${NGINX_CONF}.hero-admin.$(date +%Y%m%d%H%M%S).bak"
  cp "${NGINX_CONF}" "${backup}"

  python3 - "${NGINX_CONF}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
if "# BEGIN EV CHARGE BOOK HERO ADMIN" in text:
    raise SystemExit(0)
markers = [
    "        # =============================\n        # Frontend SPA",
    "        location / {",
]
index = next((text.find(marker) for marker in markers if text.find(marker) >= 0), -1)
if index < 0:
    raise SystemExit("Could not locate Frontend SPA insertion point in nginx.conf")
block = r'''        # BEGIN EV CHARGE BOOK HERO ADMIN
        # Mutable runtime Hero manifest. Never cache this pointer as immutable.
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

        location ^~ /ev-charge-book/hero-admin/ {
            client_max_body_size 12m;
            proxy_pass http://ev-charge-book-hero-admin:8080/;
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
        # END EV CHARGE BOOK HERO ADMIN

'''
path.write_text(text[:index] + block + text[index:], encoding="utf-8")
PY

  if ! docker exec "${NGINX_CONTAINER}" nginx -t; then
    log "Nginx validation failed; restoring ${backup}"
    cp "${backup}" "${NGINX_CONF}"
    docker exec "${NGINX_CONTAINER}" nginx -t || true
    exit 1
  fi
else
  log "Nginx Hero Admin routes already installed"
fi

CATALOG_MARKER="# BEGIN EV CHARGE BOOK VEHICLE CATALOG"
if ! grep -Fq "${CATALOG_MARKER}" "${NGINX_CONF}"; then
  log "Installing Nginx vehicle catalog route"
  backup="${NGINX_CONF}.vehicle-catalog.$(date +%Y%m%d%H%M%S).bak"
  cp "${NGINX_CONF}" "${backup}"
  python3 - "${NGINX_CONF}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
if "# BEGIN EV CHARGE BOOK VEHICLE CATALOG" in text:
    raise SystemExit(0)
marker = "        # BEGIN EV CHARGE BOOK HERO ADMIN"
index = text.find(marker)
if index < 0:
    markers = ["        # =============================\n        # Frontend SPA", "        location / {"]
    index = next((text.find(item) for item in markers if text.find(item) >= 0), -1)
if index < 0:
    raise SystemExit("Could not locate Nginx insertion point for vehicle catalog")
block = r'''        # BEGIN EV CHARGE BOOK VEHICLE CATALOG
        # Mutable remote catalog pointer. Android persists a local Room copy for offline use.
        location = /ev-charge-book/release-meta/vehicle-catalog-v1.json {
            alias /opt/ev-charge-book/release-meta/vehicle-catalog-v1.json;
            default_type application/json;
            add_header Cache-Control "no-store, no-cache, must-revalidate, max-age=0" always;
            add_header Pragma "no-cache" always;
            expires -1;
        }
        # END EV CHARGE BOOK VEHICLE CATALOG

'''
path.write_text(text[:index] + block + text[index:], encoding="utf-8")
PY

  if ! docker exec "${NGINX_CONTAINER}" nginx -t; then
    log "Nginx validation failed; restoring ${backup}"
    cp "${backup}" "${NGINX_CONF}"
    docker exec "${NGINX_CONTAINER}" nginx -t || true
    exit 1
  fi
else
  log "Nginx vehicle catalog route already installed"
fi

docker exec "${NGINX_CONTAINER}" nginx -t
docker exec "${NGINX_CONTAINER}" nginx -s reload

log "Verifying local runtime manifests"
python3 - "${MANIFEST}" "${CATALOG}" <<'PY'
import json
from pathlib import Path
import sys

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest.get("schemaVersion") == 1
assert isinstance(manifest.get("artworks"), dict) and manifest["artworks"]

catalog = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
assert catalog.get("schemaVersion") == 1
assert isinstance(catalog.get("vehicles"), list) and catalog["vehicles"]
PY

log "EV Charge Book admin deployment complete"
log "Credentials are stored only in ${ENV_FILE}"
