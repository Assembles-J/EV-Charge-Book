#!/usr/bin/env bash
set -eu

: "${VERSION_CODE:?VERSION_CODE is required}"
: "${VERSION_NAME:?VERSION_NAME is required}"
: "${APK_FILE:?APK_FILE is required}"
: "${RELEASE_SHA:?RELEASE_SHA is required}"

BASE_DIR="/opt/ev-charge-book"
UPLOAD_DIR="$BASE_DIR/release-upload"
RELEASE_DIR="$BASE_DIR/releases"
LATEST_DIR="$BASE_DIR/latest"
META_DIR="$BASE_DIR/release-meta"
PART_FILE="$UPLOAD_DIR/$APK_FILE.part"
FINAL_FILE="$RELEASE_DIR/$APK_FILE"
LATEST_FILE="$LATEST_DIR/ev-charge-book-latest.apk"
META_FILE="$META_DIR/latest.env"
RELEASE_META_FILE="$META_DIR/$VERSION_NAME.env"
UPDATE_MANIFEST="$META_DIR/latest.json"
RELEASE_UPDATE_MANIFEST="$META_DIR/$VERSION_NAME.json"

mkdir -p "$UPLOAD_DIR" "$RELEASE_DIR" "$LATEST_DIR" "$META_DIR"

test -s "$PART_FILE"

expected=""
if [ -f "$UPLOAD_DIR/$APK_FILE.sha256" ]; then
  expected="$(cut -d ' ' -f1 "$UPLOAD_DIR/$APK_FILE.sha256")"
  actual="$(sha256sum "$PART_FILE" | cut -d ' ' -f1)"
  test "$expected" = "$actual"
fi

if [ -e "$FINAL_FILE" ]; then
  echo "Release already exists: $FINAL_FILE"
  exit 1
fi

if [ -e "$RELEASE_META_FILE" ] || [ -e "$RELEASE_UPDATE_MANIFEST" ]; then
  echo "Release metadata already exists for $VERSION_NAME"
  exit 1
fi

mv "$PART_FILE" "$FINAL_FILE"
final_sha="$(sha256sum "$FINAL_FILE" | cut -d ' ' -f1)"
if [ -n "$expected" ]; then
  test "$expected" = "$final_sha"
fi

published_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

umask 022
cat > "$RELEASE_META_FILE.tmp" <<EOF
VERSION_CODE=$VERSION_CODE
VERSION_NAME=$VERSION_NAME
APK_FILE=$APK_FILE
RELEASE_SHA=$RELEASE_SHA
SHA256=$final_sha
PUBLISHED_AT=$published_at
EOF
mv "$RELEASE_META_FILE.tmp" "$RELEASE_META_FILE"

cat > "$RELEASE_UPDATE_MANIFEST.tmp" <<EOF
{
  "schemaVersion": 1,
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "apkPath": "../releases/$APK_FILE",
  "sha256": "$final_sha",
  "publishedAt": "$published_at",
  "mandatory": false
}
EOF
mv "$RELEASE_UPDATE_MANIFEST.tmp" "$RELEASE_UPDATE_MANIFEST"

cp "$RELEASE_META_FILE" "$META_FILE.tmp"
mv "$META_FILE.tmp" "$META_FILE"

# Keep the convenience APK pointer for browsers/manual download.
ln -sfn "../releases/$APK_FILE" "$LATEST_FILE"

# latest.json is the discovery pointer used by the app updater and is written last.
# A client can only discover a release after the immutable APK and per-version metadata exist.
cp "$RELEASE_UPDATE_MANIFEST" "$UPDATE_MANIFEST.tmp"
mv "$UPDATE_MANIFEST.tmp" "$UPDATE_MANIFEST"

rm -f "$UPLOAD_DIR/$APK_FILE.sha256"

echo "Published $FINAL_FILE"
echo "Latest -> $(readlink "$LATEST_FILE")"
echo "Update manifest -> $UPDATE_MANIFEST"
cat "$META_FILE"
cat "$UPDATE_MANIFEST"
