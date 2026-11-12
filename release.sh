#!/bin/bash
# pc-switch-android release script
# Usage: ./release.sh <version-name> <changelog-note>
# Example: ./release.sh 2.5.5 "Bug fix for toggle animation"

set -e

REPO_DIR="/home/finnley/projects/pc-switch-android"
DIST_DIR="/home/finnley/projects/pc-switch/dist"
APPSIGNER="/home/finnley/.android-sdk/build-tools/34.0.0/apksigner"
AAPT="/home/finnley/.android-sdk/build-tools/34.0.0/aapt"
ZIPALIGN="/home/finnley/.android-sdk/build-tools/34.0.0/zipalign"
KEYSTORE="/home/finnley/projects/pc-switch-android/pcswitch-release.keystore"
KEY_ALIAS="pcswitch"
KEY_PASS="pcswitch2026"
GITHUB_REPO="finnleyben-spec/pc-switch-android"

VERSION_NAME="$1"
CHANGELOG="$2"

if [ -z "$VERSION_NAME" ] || [ -z "$CHANGELOG" ]; then
    echo "Usage: ./release.sh <version-name> <changelog-note>"
    echo "Example: ./release.sh 2.5.5 'Bug fix for toggle animation'"
    exit 1
fi

echo "=== PC Switch Release: v$VERSION_NAME ==="
echo ""

# Step 1: Check git status
echo "[1/10] Checking git status..."
cd "$REPO_DIR"
if ! git status --porcelain -uno | grep -v "^$" > /dev/null; then
    echo "  Working tree is clean (untracked files ignored)"
else
    echo "  WARNING: Working tree has changes:"
    git status --porcelain -uno
    echo "  Continuing anyway..."
fi

# Step 2: Get current version code from build.gradle.kts
echo "[2/10] Reading current version..."
CURRENT_VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
CURRENT_CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
echo "  Current: v$CURRENT_VERSION (code $CURRENT_CODE)"

# Step 3: Bump version code
NEW_CODE=$((CURRENT_CODE + 1))
echo "[3/10] Bumping version to v$VERSION_NAME (code $NEW_CODE)..."
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts
sed -i "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$VERSION_NAME\"/" app/build.gradle.kts
echo "  Updated build.gradle.kts"

# Step 4: Build APK
echo "[4/10] Building APK (assembleRelease)..."
./gradlew assembleRelease --no-daemon
echo "  Build complete"

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
GRADLE_SIGNED="app/build/outputs/apk/release/app-release.apk"
SIGNED="app/build/outputs/apk/release/pc-switch-android-v${VERSION_NAME}.apk"

# Step 5+6: Zipalign + Sign (oder Verifikation, wenn Gradle direkt signiert)
if [ -f "$UNSIGNED" ] && "$ZIPALIGN" -c -v 4 "$UNSIGNED" >/dev/null 2>&1; then
    echo "[5/10] Zipaligning APK..."
    ALIGNED="app/build/outputs/apk/release/app-release-aligned.apk"
    "$ZIPALIGN" -f -v 4 "$UNSIGNED" "$ALIGNED"
    echo "  APK aligned"
    echo "[6/10] Signing APK..."
    "$APPSIGNER" sign \
        --ks "$KEYSTORE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEY_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --out "$SIGNED" "$ALIGNED"
    echo "  APK signed: $SIGNED"
elif [ -f "$GRADLE_SIGNED" ]; then
    # v3-Pfad: build.gradle.kts hat signingConfig -> assembleRelease liefert direkt signierte,
    # aligned APK (AGP alignt beim Packaging). Verifizieren und unter dem Asset-Namen kopieren.
    echo "[5/10] Gradle hat direkt signiert (signingConfig) -> Signature + Alignment pruefen..."
    "$APPSIGNER" verify --print-certs "$GRADLE_SIGNED"
    "$ZIPALIGN" -c -v 4 "$GRADLE_SIGNED" >/dev/null
    cp "$GRADLE_SIGNED" "$SIGNED"
    echo "  APK (Gradle-signed): $SIGNED"
else
    echo "  ERROR: Weder unsigned- noch signierte Release-APK gefunden!"
    exit 1
fi

# Step 7: Verify signature
echo "[7/10] Verifying APK signature..."
"$APPSIGNER" verify "$SIGNED"
echo "  Signature valid"

# Step 8: Verify version info in APK
echo "[8/10] Verifying version info in APK..."
APK_VERSION_NAME=$("$AAPT" dump badging "$SIGNED" | grep "package:" | grep -oP "versionName='\K[^']+")
APK_VERSION_CODE=$("$AAPT" dump badging "$SIGNED" | grep "package:" | grep -oP "versionCode='\K[0-9]+")
echo "  APK versionName: $APK_VERSION_NAME"
echo "  APK versionCode: $APK_VERSION_CODE"
if [ "$APK_VERSION_NAME" != "$VERSION_NAME" ] || [ "$APK_VERSION_CODE" != "$NEW_CODE" ]; then
    echo "  ERROR: APK version info doesn't match!"
    exit 1
fi

# Step 8: Create version.json
echo "[8/10] Creating version.json..."
APK_SHA256=$(sha256sum "$SIGNED" | awk '{print $1}')
VERSION_JSON="/tmp/version.json"
cat > "$VERSION_JSON" << EOF
{
  "versionCode": $NEW_CODE,
  "versionName": "$VERSION_NAME",
  "url": "https://github.com/$GITHUB_REPO/releases/download/v${VERSION_NAME}/pc-switch-android-v${VERSION_NAME}.apk",
  "sha256": "$APK_SHA256",
  "notes": "$CHANGELOG"
}
EOF

# Step 9: Upload to GitHub Releases
echo "[9/10] Creating GitHub release v$VERSION_NAME..."
gh release create "v$VERSION_NAME" \
    --repo "$GITHUB_REPO" \
    --title "PC Switch v$VERSION_NAME" \
    --notes "$CHANGELOG" \
    --target main \
    "$SIGNED" \
    "$VERSION_JSON"

rm "$VERSION_JSON"

# Copy to dist folder and commit
echo ""
echo "Copying APK to dist folder..."
cp "$SIGNED" "$DIST_DIR/pc-switch-v${VERSION_NAME}.apk"
"$APPSIGNER" verify "$SIGNED" > /dev/null && \
    "$APPSIGNER" sign \
        --ks "$KEYSTORE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEY_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --out "$DIST_DIR/pc-switch-v${VERSION_NAME}.apk" "$DIST_DIR/pc-switch-v${VERSION_NAME}.apk"

echo "Committing changes..."
cd "$REPO_DIR"
git add app/build.gradle.kts release.sh
if ! git diff --quiet --cached; then
    git commit -m "v$VERSION_NAME: $CHANGELOG"
    git push origin main
else
    echo "  Nothing new to commit (changes already pushed)"
fi

echo ""
echo "=== RELEASE COMPLETE ==="
echo "Version: v$VERSION_NAME (code $NEW_CODE)"
echo "GitHub: https://github.com/$GITHUB_REPO/releases/tag/v${VERSION_NAME}"
echo "APK: $DIST_DIR/pc-switch-v${VERSION_NAME}.apk"
