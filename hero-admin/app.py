from __future__ import annotations

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

TARGET_SIZE = (1600, 1100)
MIN_WIDTH = 1200
MIN_HEIGHT = 800
MIN_RATIO = 1.40
MAX_RATIO = 1.55
MAX_OUTPUT_BYTES = 2_621_440  # 2.5 MiB
ALLOWED_INPUT_FORMATS = {"PNG", "WEBP"}
KEY_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{1,100}$")
POWERTRAIN_TYPES = {"BEV", "PHEV", "REEV"}


def _startup_guard() -> None:
    if not ADMIN_PASSWORD:
        raise RuntimeError("HERO_ADMIN_PASSWORD must be set; refusing to start an open admin service")
    HERO_DIR.mkdir(parents=True, exist_ok=True)
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


def _validate_catalog(catalog: dict) -> dict:
    if catalog.get("schemaVersion") != 1:
        raise ValueError("unsupported vehicle catalog schemaVersion")
    vehicles = catalog.get("vehicles")
    if not isinstance(vehicles, list) or not vehicles:
        raise ValueError("vehicle catalog has no vehicles")
    seen: set[str] = set()
    for item in vehicles:
        if not isinstance(item, dict):
            raise ValueError("vehicle catalog contains an invalid item")
        catalog_id = str(item.get("catalogId") or "")
        if not KEY_PATTERN.fullmatch(catalog_id) or catalog_id in seen:
            raise ValueError(f"invalid or duplicate catalogId: {catalog_id}")
        seen.add(catalog_id)
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


def _normalize_catalog_item(payload: dict, existing: dict | None = None) -> dict:
    catalog_id = str(payload.get("catalogId") or "").strip().lower()
    if not KEY_PATTERN.fullmatch(catalog_id):
        raise ValueError("catalogId 只允许小写字母、数字和连字符")
    if existing is not None and catalog_id != existing.get("catalogId"):
        raise ValueError("catalogId 是稳定标识，已有车型不能改 ID；请新建车型后下架旧项")

    brand = str(payload.get("brand") or "").strip()
    series = str(payload.get("series") or "").strip()
    model_name = str(payload.get("modelName") or "").strip()
    trim_name = str(payload.get("trimName") or "").strip() or None
    powertrain = str(payload.get("powertrainType") or "").strip().upper()
    hero_key = str(payload.get("heroArtworkKey") or "").strip().lower() or None

    if not brand or not series or not model_name:
        raise ValueError("品牌、车系、车型名称不能为空")
    if powertrain not in POWERTRAIN_TYPES:
        raise ValueError("动力类型只能是 BEV / PHEV / REEV")
    if hero_key is not None and not KEY_PATTERN.fullmatch(hero_key):
        raise ValueError("Hero key 只允许小写字母、数字和连字符")

    model_year = _catalog_number(payload.get("modelYear"), "年款", integer=True)
    if model_year is not None and not (1990 <= model_year <= 2100):
        raise ValueError("年款超出合理范围")

    now = int(time.time() * 1000)
    return {
        "catalogId": catalog_id,
        "source": "managed-v1",
        "brand": brand,
        "series": series,
        "modelName": model_name,
        "modelYear": model_year,
        "trimName": trim_name,
        "powertrainType": powertrain,
        "batteryCapacityKwh": _catalog_number(payload.get("batteryCapacityKwh"), "电池容量"),
        "rangeKm": _catalog_number(payload.get("rangeKm"), "标称续航", integer=True),
        "heroArtworkKey": hero_key,
        "isActive": bool(existing.get("isActive", True) if existing else True),
        "sourceUpdatedAtEpochMillis": now,
    }


def _bump_catalog(catalog: dict) -> None:
    catalog["catalogVersion"] = max(0, int(catalog.get("catalogVersion") or 0)) + 1
    catalog["updatedAtEpochMillis"] = int(time.time() * 1000)


def _safe_slug(key: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_")


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
        normalized = _normalize_catalog_item(payload, existing)
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
