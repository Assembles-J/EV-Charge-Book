from __future__ import annotations

import hashlib
import hmac
import io
import json
import os
import re
import shutil
import tempfile
import time
import urllib.request
from functools import wraps
from pathlib import Path

from flask import Flask, Response, jsonify, render_template, request
from PIL import Image, ImageOps

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 12 * 1024 * 1024

ADMIN_USER = os.environ.get("HERO_ADMIN_USER", "admin")
ADMIN_PASSWORD = os.environ.get("HERO_ADMIN_PASSWORD", "")
PUBLIC_ORIGIN = os.environ.get("HERO_PUBLIC_ORIGIN", "https://groupim.cn").rstrip("/")
PUBLIC_ASSET_BASE = os.environ.get(
    "HERO_PUBLIC_ASSET_BASE",
    "https://groupim.cn/ev-charge-book/releases/hero-assets",
).rstrip("/")
BRAND_LOGO_ASSET_BASE = os.environ.get(
    "BRAND_LOGO_PUBLIC_ASSET_BASE",
    "https://groupim.cn/ev-charge-book/releases/brand-logos",
).rstrip("/")
MANIFEST_SEED_URL = os.environ.get(
    "HERO_MANIFEST_SEED_URL",
    "https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/hero-assets/manifest-v1.json",
)
CATALOG_SEED_URL = os.environ.get(
    "VEHICLE_CATALOG_SEED_URL",
    "https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/vehicle-catalog/catalog-v1.json",
)
RELEASE_ROOT = Path(os.environ.get("HERO_RELEASE_ROOT", "/data/releases"))
META_ROOT = Path(os.environ.get("HERO_META_ROOT", "/data/release-meta"))
MANIFEST_PATH = META_ROOT / "hero-assets-v1.json"
CATALOG_PATH = META_ROOT / "vehicle-catalog-v1.json"
HERO_DIR = RELEASE_ROOT / "hero-assets"
BRAND_LOGO_DIR = RELEASE_ROOT / "brand-logos"

TARGET_SIZE = (1600, 1100)
MIN_WIDTH = 1200
MIN_HEIGHT = 800
MIN_RATIO = 1.40
MAX_RATIO = 1.55
MAX_OUTPUT_BYTES = 2_621_440  # 2.5 MiB

LOGO_TARGET_SIZE = (512, 512)
LOGO_SQUARE_VISUAL_MAX = (384, 384)
LOGO_WORDMARK_VISUAL_MAX = (416, 192)
LOGO_HARD_MAX_BYTES = 262_144  # 256 KiB
LOGO_MIN_SOURCE_EDGE = 512

ALLOWED_INPUT_FORMATS = {"PNG", "WEBP"}
KEY_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{1,100}$")
POWERTRAIN_TYPES = {"BEV", "PHEV", "REEV"}
LOGO_VARIANTS = {"light", "dark"}


def _startup_guard() -> None:
    if not ADMIN_PASSWORD:
        raise RuntimeError("HERO_ADMIN_PASSWORD must be set; refusing to start an open admin service")
    HERO_DIR.mkdir(parents=True, exist_ok=True)
    BRAND_LOGO_DIR.mkdir(parents=True, exist_ok=True)
    META_ROOT.mkdir(parents=True, exist_ok=True)


_startup_guard()


def _authorized() -> bool:
    auth = request.authorization
    if auth is None:
        return False
    return hmac.compare_digest(auth.username or "", ADMIN_USER) and hmac.compare_digest(
        auth.password or "", ADMIN_PASSWORD
    )


def require_auth(view):
    @wraps(view)
    def wrapper(*args, **kwargs):
        if not _authorized():
            return Response(
                "Authentication required",
                401,
                {"WWW-Authenticate": 'Basic realm="EV Charge Book Admin"'},
            )
        return view(*args, **kwargs)

    return wrapper


def require_admin_post() -> Response | None:
    if request.headers.get("X-Hero-Admin-Request") != "1":
        return jsonify({"error": "missing admin request header"}), 403
    origin = request.headers.get("Origin")
    if origin and origin.rstrip("/") != PUBLIC_ORIGIN:
        return jsonify({"error": "origin rejected"}), 403
    return None


def _write_atomic(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(payload)
            os.fchmod(handle.fileno(), 0o644)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


def _load_seed(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def _validate_manifest(manifest: dict) -> dict:
    if manifest.get("schemaVersion") != 1:
        raise ValueError("unsupported Hero manifest schemaVersion")
    artworks = manifest.get("artworks")
    if not isinstance(artworks, dict) or not artworks:
        raise ValueError("Hero manifest has no artworks")
    return manifest


def _ensure_manifest() -> None:
    if MANIFEST_PATH.is_file():
        return
    try:
        seed = _load_seed(MANIFEST_SEED_URL)
        _validate_manifest(seed)
        _write_atomic(
            MANIFEST_PATH,
            (json.dumps(seed, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
        )
    except Exception as exc:
        raise FileNotFoundError(
            f"Hero manifest is missing and seed download failed: {MANIFEST_SEED_URL}"
        ) from exc


def _load_manifest() -> dict:
    _ensure_manifest()
    return _validate_manifest(json.loads(MANIFEST_PATH.read_text(encoding="utf-8")))


def _write_manifest_atomic(manifest: dict) -> None:
    if MANIFEST_PATH.exists():
        shutil.copy2(MANIFEST_PATH, MANIFEST_PATH.with_suffix(".json.bak"))
    _write_atomic(
        MANIFEST_PATH,
        (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
    )


def _safe_slug(key: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_")


def _legacy_brand_id(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    if len(slug) >= 2:
        return slug[:80]
    digest = hashlib.sha1(name.encode("utf-8")).hexdigest()[:12]
    return f"brand-{digest}"


def _upgrade_legacy_catalog(catalog: dict) -> dict:
    vehicles = catalog.get("vehicles")
    if not isinstance(vehicles, list):
        return catalog

    brands = catalog.get("brands")
    if not isinstance(brands, list):
        brands = []
        catalog["brands"] = brands

    by_name = {
        str(item.get("name") or "").strip(): item
        for item in brands
        if isinstance(item, dict) and str(item.get("name") or "").strip()
    }
    by_id = {
        str(item.get("brandId") or "").strip().lower(): item
        for item in brands
        if isinstance(item, dict) and str(item.get("brandId") or "").strip()
    }

    for vehicle in vehicles:
        if not isinstance(vehicle, dict):
            continue
        brand_name = str(vehicle.get("brand") or "").strip()
        brand_id = str(vehicle.get("brandId") or "").strip().lower()
        if brand_id and brand_id in by_id:
            vehicle["brand"] = str(by_id[brand_id].get("name") or brand_name).strip()
            continue
        existing = by_name.get(brand_name)
        if existing is None and brand_name:
            candidate = _legacy_brand_id(brand_name)
            suffix = 2
            original = candidate
            while candidate in by_id:
                candidate = f"{original}-{suffix}"
                suffix += 1
            existing = {
                "brandId": candidate,
                "name": brand_name,
                "englishName": None,
                "logoKey": f"brand-{candidate}",
                "logoLightUrl": None,
                "logoLightVersion": 0,
                "logoDarkUrl": None,
                "logoDarkVersion": 0,
                "isActive": True,
                "sourceUpdatedAtEpochMillis": 0,
            }
            brands.append(existing)
            by_name[brand_name] = existing
            by_id[candidate] = existing
        if existing is not None:
            vehicle["brandId"] = existing["brandId"]
            vehicle["brand"] = existing["name"]
    return catalog


def _validate_optional_https_url(value, name: str) -> str | None:
    if value in (None, ""):
        return None
    result = str(value).strip()
    if not result.startswith("https://"):
        raise ValueError(f"{name} 必须使用 https")
    return result


def _validate_catalog(catalog: dict) -> dict:
    if catalog.get("schemaVersion") != 1:
        raise ValueError("unsupported vehicle catalog schemaVersion")

    _upgrade_legacy_catalog(catalog)
    brands = catalog.get("brands")
    vehicles = catalog.get("vehicles")
    if not isinstance(brands, list) or not brands:
        raise ValueError("vehicle catalog has no brands")
    if not isinstance(vehicles, list) or not vehicles:
        raise ValueError("vehicle catalog has no vehicles")

    brands_by_id: dict[str, dict] = {}
    for brand in brands:
        if not isinstance(brand, dict):
            raise ValueError("vehicle catalog contains an invalid brand")
        brand_id = str(brand.get("brandId") or "").strip().lower()
        name = str(brand.get("name") or "").strip()
        if not KEY_PATTERN.fullmatch(brand_id) or brand_id in brands_by_id:
            raise ValueError(f"invalid or duplicate brandId: {brand_id}")
        if not name:
            raise ValueError(f"brand name is blank: {brand_id}")
        brand["brandId"] = brand_id
        brand["name"] = name
        brand["logoLightUrl"] = _validate_optional_https_url(brand.get("logoLightUrl"), "logoLightUrl")
        brand["logoDarkUrl"] = _validate_optional_https_url(brand.get("logoDarkUrl"), "logoDarkUrl")
        light_version = int(brand.get("logoLightVersion") or 0)
        dark_version = int(brand.get("logoDarkVersion") or 0)
        if light_version < 0 or dark_version < 0:
            raise ValueError("brand logo version cannot be negative")
        if brand["logoLightUrl"] is None and light_version != 0:
            raise ValueError(f"light logo version without URL: {brand_id}")
        if brand["logoDarkUrl"] is None and dark_version != 0:
            raise ValueError(f"dark logo version without URL: {brand_id}")
        brand["logoLightVersion"] = light_version
        brand["logoDarkVersion"] = dark_version
        brands_by_id[brand_id] = brand

    seen: set[str] = set()
    for item in vehicles:
        if not isinstance(item, dict):
            raise ValueError("vehicle catalog contains an invalid item")
        catalog_id = str(item.get("catalogId") or "").strip().lower()
        if not KEY_PATTERN.fullmatch(catalog_id) or catalog_id in seen:
            raise ValueError(f"invalid or duplicate catalogId: {catalog_id}")
        seen.add(catalog_id)
        item["catalogId"] = catalog_id
        brand_id = str(item.get("brandId") or "").strip().lower()
        brand = brands_by_id.get(brand_id)
        if brand is None:
            raise ValueError(f"unknown brandId for {catalog_id}: {brand_id}")
        item["brandId"] = brand_id
        item["brand"] = brand["name"]
    return catalog


def _ensure_catalog() -> None:
    if CATALOG_PATH.is_file():
        return
    try:
        seed = _load_seed(CATALOG_SEED_URL)
        _validate_catalog(seed)
        _write_atomic(
            CATALOG_PATH,
            (json.dumps(seed, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
        )
    except Exception as exc:
        raise FileNotFoundError(
            f"vehicle catalog is missing and seed download failed: {CATALOG_SEED_URL}"
        ) from exc


def _load_catalog() -> dict:
    _ensure_catalog()
    return _validate_catalog(json.loads(CATALOG_PATH.read_text(encoding="utf-8")))


def _write_catalog_atomic(catalog: dict) -> None:
    _validate_catalog(catalog)
    if CATALOG_PATH.exists():
        shutil.copy2(CATALOG_PATH, CATALOG_PATH.with_suffix(".json.bak"))
    _write_atomic(
        CATALOG_PATH,
        (json.dumps(catalog, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
    )


def _catalog_number(value, name: str, *, integer: bool = False):
    if value in (None, ""):
        return None
    try:
        result = int(value) if integer else float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name} 格式不正确") from exc
    if result <= 0:
        raise ValueError(f"{name} 必须大于 0")
    return result


def _normalize_brand(payload: dict, existing: dict | None = None) -> dict:
    brand_id = str(payload.get("brandId") or "").strip().lower()
    if not KEY_PATTERN.fullmatch(brand_id):
        raise ValueError("brandId 只允许小写字母、数字和连字符")
    if existing is not None and brand_id != existing.get("brandId"):
        raise ValueError("brandId 是稳定标识，已有品牌不能修改 ID")

    name = str(payload.get("name") or "").strip()
    english_name = str(payload.get("englishName") or "").strip() or None
    if not name:
        raise ValueError("品牌名称不能为空")
    if len(name) > 80 or (english_name is not None and len(english_name) > 120):
        raise ValueError("品牌名称过长")

    now = int(time.time() * 1000)
    current = existing or {}
    return {
        "brandId": brand_id,
        "name": name,
        "englishName": english_name,
        "logoKey": str(current.get("logoKey") or f"brand-{brand_id}"),
        "logoLightUrl": current.get("logoLightUrl"),
        "logoLightVersion": int(current.get("logoLightVersion") or 0),
        "logoDarkUrl": current.get("logoDarkUrl"),
        "logoDarkVersion": int(current.get("logoDarkVersion") or 0),
        "isActive": bool(current.get("isActive", True)),
        "sourceUpdatedAtEpochMillis": now,
    }


def _normalize_catalog_item(payload: dict, catalog: dict, existing: dict | None = None) -> dict:
    catalog_id = str(payload.get("catalogId") or "").strip().lower()
    if not KEY_PATTERN.fullmatch(catalog_id):
        raise ValueError("catalogId 只允许小写字母、数字和连字符")
    if existing is not None and catalog_id != existing.get("catalogId"):
        raise ValueError("catalogId 是稳定标识，已有车型不能改 ID；请新建车型后下架旧项")

    brand_id = str(payload.get("brandId") or "").strip().lower()
    brand = next((item for item in catalog["brands"] if item.get("brandId") == brand_id), None)
    if brand is None:
        raise ValueError("请选择有效品牌")
    series = str(payload.get("series") or "").strip()
    model_name = str(payload.get("modelName") or "").strip()
    trim_name = str(payload.get("trimName") or "").strip() or None
    powertrain = str(payload.get("powertrainType") or "").strip().upper()
    range_standard = str(payload.get("rangeStandard") or "").strip().upper() or None
    hero_key = str(payload.get("heroArtworkKey") or "").strip().lower() or None

    if not series or not model_name:
        raise ValueError("车系、车型名称不能为空")
    if powertrain not in POWERTRAIN_TYPES:
        raise ValueError("动力类型只能是 BEV / PHEV / REEV")
    if range_standard is not None and len(range_standard) > 32:
        raise ValueError("续航标准过长")
    if hero_key is not None and not KEY_PATTERN.fullmatch(hero_key):
        raise ValueError("Hero key 只允许小写字母、数字和连字符")

    model_year = _catalog_number(payload.get("modelYear"), "年款", integer=True)
    if model_year is not None and not (1990 <= model_year <= 2100):
        raise ValueError("年款超出合理范围")

    now = int(time.time() * 1000)
    return {
        "catalogId": catalog_id,
        "source": "managed-v1",
        "brandId": brand_id,
        "brand": brand["name"],
        "series": series,
        "modelName": model_name,
        "modelYear": model_year,
        "trimName": trim_name,
        "powertrainType": powertrain,
        "batteryCapacityKwh": _catalog_number(payload.get("batteryCapacityKwh"), "电池容量"),
        "rangeKm": _catalog_number(payload.get("rangeKm"), "标称续航", integer=True),
        "rangeStandard": range_standard,
        "heroArtworkKey": hero_key,
        "isActive": bool(existing.get("isActive", True) if existing else True),
        "sourceUpdatedAtEpochMillis": now,
    }


def _bump_catalog(catalog: dict) -> None:
    catalog["catalogVersion"] = max(0, int(catalog.get("catalogVersion") or 0)) + 1
    catalog["updatedAtEpochMillis"] = int(time.time() * 1000)


def _decode_and_validate(upload_bytes: bytes) -> tuple[Image.Image, dict]:
    try:
        source = Image.open(io.BytesIO(upload_bytes))
        source.load()
    except Exception as exc:
        raise ValueError("图片无法解析，请上传有效 PNG 或 WebP") from exc

    source_format = (source.format or "").upper()
    if source_format not in ALLOWED_INPUT_FORMATS:
        raise ValueError("只接受 PNG 或 WebP 源图")

    source = ImageOps.exif_transpose(source)
    width, height = source.size
    if width < MIN_WIDTH or height < MIN_HEIGHT:
        raise ValueError(f"图片分辨率过低：{width}×{height}，至少需要 {MIN_WIDTH}×{MIN_HEIGHT}")

    ratio = width / height
    if not (MIN_RATIO <= ratio <= MAX_RATIO):
        raise ValueError(
            f"Hero 比例不符合要求：{ratio:.2f}:1；请使用约 1.45:1 的横图（建议 1600×1100）"
        )

    metadata = {
        "sourceFormat": source_format,
        "sourceWidth": width,
        "sourceHeight": height,
        "sourceRatio": round(ratio, 3),
    }
    return source.convert("RGB"), metadata


def _encode_webp(source: Image.Image) -> tuple[bytes, int]:
    fitted = ImageOps.fit(source, TARGET_SIZE, method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    for quality in (88, 84, 80, 76):
        buffer = io.BytesIO()
        fitted.save(buffer, format="WEBP", quality=quality, method=6)
        payload = buffer.getvalue()
        if len(payload) <= MAX_OUTPUT_BYTES:
            if len(payload) < 12 or payload[:4] != b"RIFF" or payload[8:12] != b"WEBP":
                raise ValueError("WebP 编码结果校验失败")
            return payload, quality
    raise ValueError("转换后的 WebP 仍超过 2.5 MiB，请降低源图复杂度")


def _decode_logo(upload_bytes: bytes) -> tuple[Image.Image, dict]:
    try:
        source = Image.open(io.BytesIO(upload_bytes))
        source.load()
    except Exception as exc:
        raise ValueError("Logo 无法解析，请上传有效 PNG 或 WebP") from exc

    source_format = (source.format or "").upper()
    if source_format not in ALLOWED_INPUT_FORMATS:
        raise ValueError("Logo 只接受 PNG 或 WebP")
    source = ImageOps.exif_transpose(source).convert("RGBA")
    width, height = source.size
    if max(width, height) < LOGO_MIN_SOURCE_EDGE:
        raise ValueError("Logo 源图分辨率过低；最长边至少需要 512 px")

    alpha = source.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("Logo 图片完全透明")
    artwork = source.crop(bbox)
    if max(artwork.size) < 256:
        raise ValueError("Logo 有效图形分辨率过低")

    ratio = artwork.width / max(1, artwork.height)
    visual_max = LOGO_WORDMARK_VISUAL_MAX if ratio >= 1.8 else LOGO_SQUARE_VISUAL_MAX
    contained = ImageOps.contain(artwork, visual_max, method=Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", LOGO_TARGET_SIZE, (0, 0, 0, 0))
    x = (LOGO_TARGET_SIZE[0] - contained.width) // 2
    y = (LOGO_TARGET_SIZE[1] - contained.height) // 2
    canvas.alpha_composite(contained, (x, y))
    return canvas, {
        "sourceFormat": source_format,
        "sourceWidth": width,
        "sourceHeight": height,
        "visualWidth": contained.width,
        "visualHeight": contained.height,
    }


def _encode_logo_webp(source: Image.Image) -> tuple[bytes, str]:
    attempts = [
        ("lossless", {"lossless": True, "method": 6}),
        ("q96", {"quality": 96, "method": 6}),
        ("q92", {"quality": 92, "method": 6}),
        ("q88", {"quality": 88, "method": 6}),
        ("q84", {"quality": 84, "method": 6}),
    ]
    for label, options in attempts:
        buffer = io.BytesIO()
        source.save(buffer, format="WEBP", **options)
        payload = buffer.getvalue()
        if len(payload) <= LOGO_HARD_MAX_BYTES:
            if len(payload) < 12 or payload[:4] != b"RIFF" or payload[8:12] != b"WEBP":
                raise ValueError("Logo WebP 编码结果校验失败")
            return payload, label
    raise ValueError("标准化后的 Logo 仍超过 256 KiB，请使用更简洁的官方源图")


@app.get("/")
@require_auth
def index():
    return render_template("index.html")


@app.get("/healthz")
def healthz():
    return jsonify({"ok": True})


@app.get("/api/state")
@require_auth
def state():
    try:
        return jsonify(_load_manifest())
    except Exception as exc:
        return jsonify({"error": str(exc)}), 503


@app.get("/api/catalog/state")
@require_auth
def catalog_state():
    try:
        return jsonify(_load_catalog())
    except Exception as exc:
        return jsonify({"error": str(exc)}), 503


@app.post("/api/brand/save")
@require_auth
def brand_save():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected
    try:
        payload = request.get_json(force=True, silent=False) or {}
        catalog = _load_catalog()
        brands = catalog["brands"]
        brand_id = str(payload.get("brandId") or "").strip().lower()
        existing = next((item for item in brands if item.get("brandId") == brand_id), None)
        normalized = _normalize_brand(payload, existing)
        if existing is None:
            brands.append(normalized)
            action = "created"
        else:
            old_name = existing.get("name")
            brands[brands.index(existing)] = normalized
            action = "updated"
            if normalized["name"] != old_name:
                for vehicle in catalog["vehicles"]:
                    if vehicle.get("brandId") == brand_id:
                        vehicle["brand"] = normalized["name"]
                        vehicle["sourceUpdatedAtEpochMillis"] = int(time.time() * 1000)
        brands.sort(key=lambda item: (item.get("name") or "", item.get("brandId") or ""))
        _bump_catalog(catalog)
        _write_catalog_atomic(catalog)
        return jsonify({"ok": True, "action": action, "catalogVersion": catalog["catalogVersion"], "brand": normalized})
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Brand save failed")
        return jsonify({"error": f"保存品牌失败：{exc}"}), 500


@app.post("/api/brand/status")
@require_auth
def brand_status():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected
    try:
        payload = request.get_json(force=True, silent=False) or {}
        brand_id = str(payload.get("brandId") or "").strip().lower()
        active = payload.get("isActive")
        if not KEY_PATTERN.fullmatch(brand_id) or not isinstance(active, bool):
            raise ValueError("无效的品牌状态请求")
        catalog = _load_catalog()
        brand = next((entry for entry in catalog["brands"] if entry.get("brandId") == brand_id), None)
        if brand is None:
            return jsonify({"error": "品牌不存在"}), 404
        brand["isActive"] = active
        brand["sourceUpdatedAtEpochMillis"] = int(time.time() * 1000)
        _bump_catalog(catalog)
        _write_catalog_atomic(catalog)
        return jsonify({"ok": True, "catalogVersion": catalog["catalogVersion"], "brand": brand})
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Brand status update failed")
        return jsonify({"error": f"更新品牌状态失败：{exc}"}), 500


@app.post("/api/brand/logo")
@require_auth
def brand_logo_publish():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected

    brand_id = (request.form.get("brandId") or "").strip().lower()
    variant = (request.form.get("variant") or "").strip().lower()
    if not KEY_PATTERN.fullmatch(brand_id):
        return jsonify({"error": "无效的 brandId"}), 400
    if variant not in LOGO_VARIANTS:
        return jsonify({"error": "Logo variant 只能是 light / dark"}), 400
    upload = request.files.get("file")
    if upload is None or not upload.filename:
        return jsonify({"error": "请选择 PNG 或 WebP Logo"}), 400

    try:
        catalog = _load_catalog()
        brand = next((entry for entry in catalog["brands"] if entry.get("brandId") == brand_id), None)
        if brand is None:
            return jsonify({"error": "品牌不存在"}), 404
        raw = upload.read()
        if not raw:
            return jsonify({"error": "上传文件为空"}), 400

        normalized, metadata = _decode_logo(raw)
        webp_bytes, encode_mode = _encode_logo_webp(normalized)
        version_key = "logoLightVersion" if variant == "light" else "logoDarkVersion"
        url_key = "logoLightUrl" if variant == "light" else "logoDarkUrl"
        next_version = int(brand.get(version_key) or 0) + 1
        filename = f"brand_{_safe_slug(brand_id)}_{variant}_v{next_version}.webp"
        destination = BRAND_LOGO_DIR / filename
        if destination.exists():
            return jsonify({"error": f"目标 Logo 已存在：{filename}"}), 409

        _write_atomic(destination, webp_bytes)
        public_url = f"{BRAND_LOGO_ASSET_BASE}/{filename}"
        brand[url_key] = public_url
        brand[version_key] = next_version
        brand["logoKey"] = brand.get("logoKey") or f"brand-{brand_id}"
        brand["sourceUpdatedAtEpochMillis"] = int(time.time() * 1000)
        _bump_catalog(catalog)
        _write_catalog_atomic(catalog)

        return jsonify({
            "ok": True,
            "brandId": brand_id,
            "variant": variant,
            "version": next_version,
            "url": public_url,
            "filename": filename,
            "outputBytes": len(webp_bytes),
            "outputWidth": LOGO_TARGET_SIZE[0],
            "outputHeight": LOGO_TARGET_SIZE[1],
            "encodeMode": encode_mode,
            "catalogVersion": catalog["catalogVersion"],
            **metadata,
        })
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Brand Logo publish failed")
        return jsonify({"error": f"发布品牌 Logo 失败：{exc}"}), 500


@app.post("/api/catalog/save")
@require_auth
def catalog_save():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected
    try:
        payload = request.get_json(force=True, silent=False) or {}
        catalog = _load_catalog()
        vehicles = catalog["vehicles"]
        catalog_id = str(payload.get("catalogId") or "").strip().lower()
        existing = next((item for item in vehicles if item.get("catalogId") == catalog_id), None)
        normalized = _normalize_catalog_item(payload, catalog, existing)
        if existing is None:
            vehicles.append(normalized)
            action = "created"
        else:
            vehicles[vehicles.index(existing)] = normalized
            action = "updated"
        vehicles.sort(key=lambda item: (item.get("brand") or "", item.get("series") or "", -(item.get("modelYear") or 0), item.get("trimName") or ""))
        _bump_catalog(catalog)
        _write_catalog_atomic(catalog)
        return jsonify({"ok": True, "action": action, "catalogVersion": catalog["catalogVersion"], "vehicle": normalized})
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Vehicle catalog save failed")
        return jsonify({"error": f"保存车型失败：{exc}"}), 500


@app.post("/api/catalog/status")
@require_auth
def catalog_status():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected
    try:
        payload = request.get_json(force=True, silent=False) or {}
        catalog_id = str(payload.get("catalogId") or "").strip().lower()
        active = payload.get("isActive")
        if not KEY_PATTERN.fullmatch(catalog_id) or not isinstance(active, bool):
            raise ValueError("无效的车型状态请求")
        catalog = _load_catalog()
        item = next((entry for entry in catalog["vehicles"] if entry.get("catalogId") == catalog_id), None)
        if item is None:
            return jsonify({"error": "车型不存在"}), 404
        item["isActive"] = active
        item["sourceUpdatedAtEpochMillis"] = int(time.time() * 1000)
        _bump_catalog(catalog)
        _write_catalog_atomic(catalog)
        return jsonify({"ok": True, "catalogVersion": catalog["catalogVersion"], "vehicle": item})
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Vehicle catalog status update failed")
        return jsonify({"error": f"更新车型状态失败：{exc}"}), 500


@app.post("/api/publish")
@require_auth
def publish():
    rejected = require_admin_post()
    if rejected is not None:
        return rejected

    key = (request.form.get("artworkKey") or "").strip().lower()
    if not KEY_PATTERN.fullmatch(key):
        return jsonify({"error": "无效的 artwork key"}), 400
    upload = request.files.get("file")
    if upload is None or not upload.filename:
        return jsonify({"error": "请选择 PNG 或 WebP 图片"}), 400

    try:
        manifest = _load_manifest()
        artworks = manifest["artworks"]
        current = artworks.get(key)
        raw = upload.read()
        if not raw:
            return jsonify({"error": "上传文件为空"}), 400

        source, metadata = _decode_and_validate(raw)
        webp_bytes, quality = _encode_webp(source)
        current_version = int(current.get("version") or 0) if isinstance(current, dict) else 0
        next_version = current_version + 1
        filename = f"{_safe_slug(key)}_v{next_version}.webp"
        destination = HERO_DIR / filename
        if destination.exists():
            return jsonify({"error": f"目标文件已存在：{filename}；请检查 manifest 版本"}), 409

        _write_atomic(destination, webp_bytes)
        public_url = f"{PUBLIC_ASSET_BASE}/{filename}"
        artworks[key] = {"version": next_version, "url": public_url}
        _write_manifest_atomic(manifest)

        return jsonify({
            "ok": True,
            "artworkKey": key,
            "version": next_version,
            "url": public_url,
            "filename": filename,
            "outputBytes": len(webp_bytes),
            "outputWidth": TARGET_SIZE[0],
            "outputHeight": TARGET_SIZE[1],
            "quality": quality,
            **metadata,
        })
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except Exception as exc:
        app.logger.exception("Hero publish failed")
        return jsonify({"error": f"发布失败：{exc}"}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
