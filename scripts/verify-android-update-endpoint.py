#!/usr/bin/env python3
"""Verify the public Android update manifest and its immutable APK target."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def cache_busted(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    query.append(("_releaseProbe", str(int(time.time() * 1000))))
    return urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, parsed.path, urllib.parse.urlencode(query), parsed.fragment)
    )


def open_request(url: str, *, method: str = "GET", range_first_byte: bool = False):
    headers = {
        "User-Agent": "EV-Charge-Book-Release-Check/1",
        "Cache-Control": "no-cache, no-store, max-age=0",
        "Pragma": "no-cache",
    }
    if range_first_byte:
        headers["Range"] = "bytes=0-0"
    request = urllib.request.Request(url, headers=headers, method=method)
    return urllib.request.urlopen(request, timeout=15)


def fetch_manifest(url: str) -> dict:
    request_url = cache_busted(url)
    with open_request(request_url) as response:
        status = getattr(response, "status", 200)
        if not 200 <= status <= 299:
            raise RuntimeError(f"manifest returned HTTP {status}")
        payload = response.read().decode("utf-8")
    try:
        manifest = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"manifest is not valid JSON: {exc}") from exc
    if manifest.get("schemaVersion") != 1:
        raise RuntimeError(f"unsupported schemaVersion: {manifest.get('schemaVersion')!r}")
    if not isinstance(manifest.get("versionCode"), int):
        raise RuntimeError("manifest versionCode is missing or not an integer")
    if not isinstance(manifest.get("versionName"), str) or not manifest["versionName"]:
        raise RuntimeError("manifest versionName is missing")
    if not isinstance(manifest.get("apkPath"), str) or not manifest["apkPath"]:
        raise RuntimeError("manifest apkPath is missing")
    sha256 = manifest.get("sha256")
    if not isinstance(sha256, str) or len(sha256) != 64 or any(c not in "0123456789abcdefABCDEF" for c in sha256):
        raise RuntimeError("manifest sha256 is invalid")
    return manifest


def check_apk(url: str) -> None:
    try:
        with open_request(url, method="HEAD") as response:
            status = getattr(response, "status", 200)
            if 200 <= status <= 299:
                return
    except urllib.error.HTTPError as exc:
        if exc.code not in (405, 501):
            raise RuntimeError(f"immutable APK HEAD failed with HTTP {exc.code}: {url}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"immutable APK is unreachable: {url}: {exc.reason}") from exc

    try:
        with open_request(url, range_first_byte=True) as response:
            status = getattr(response, "status", 200)
            if status not in (200, 206):
                raise RuntimeError(f"immutable APK range GET returned HTTP {status}: {url}")
            response.read(1)
    except urllib.error.URLError as exc:
        raise RuntimeError(f"immutable APK is unreachable: {url}: {exc.reason}") from exc


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest-url", required=True)
    parser.add_argument("--expected-version-code", type=int)
    parser.add_argument("--expected-version-name")
    parser.add_argument("--check-apk", action="store_true")
    args = parser.parse_args()

    try:
        manifest = fetch_manifest(args.manifest_url)
        if args.expected_version_code is not None and manifest["versionCode"] != args.expected_version_code:
            raise RuntimeError(
                f"public manifest versionCode={manifest['versionCode']} expected={args.expected_version_code}"
            )
        if args.expected_version_name is not None and manifest["versionName"] != args.expected_version_name:
            raise RuntimeError(
                f"public manifest versionName={manifest['versionName']!r} expected={args.expected_version_name!r}"
            )

        apk_url = urllib.parse.urljoin(args.manifest_url, manifest["apkPath"])
        if args.check_apk:
            check_apk(apk_url)

        print(
            json.dumps(
                {
                    "manifestUrl": args.manifest_url,
                    "versionCode": manifest["versionCode"],
                    "versionName": manifest["versionName"],
                    "apkUrl": apk_url,
                },
                ensure_ascii=False,
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001 - release gate must print the actual discovery failure.
        print(f"Public Android update endpoint verification failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
