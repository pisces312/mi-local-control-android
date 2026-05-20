#!/usr/bin/env bash
# 米控 Build Script
# Usage: ./build.sh [debug|release]
#   (default: release)

set -e

BUILD_TYPE="${1:-release}"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$PROJECT_DIR/app"
KEYSTORE="${KEY_STORE_LOCATION:-}"
KEYSTORE_PASS="${KEY_STORE_PASSWORD:-}"
KEY_ALIAS="${KEY_ALIAS:-}"

# Auto-detect version from build.gradle.kts
VERSION=""
GRADLE_FILE="$APP_DIR/build.gradle.kts"
if [[ -f "$GRADLE_FILE" ]]; then
    VERSION=$(grep 'versionName' "$GRADLE_FILE" | head -1 | sed 's/.*versionName *= *"\([^"]*\)".*/\1/')
fi
if [[ -z "$VERSION" ]]; then
    VERSION="1.0.0"
fi
VERSION="v$VERSION"

# Validate BUILD_TYPE
case "$BUILD_TYPE" in
    debug|release) ;;
    *) echo "Usage: $0 [debug|release]"; exit 1 ;;
esac

BUILD_TYPE_CAP="$(echo "$BUILD_TYPE" | sed 's/\b./\u&/')"
GRADLE_TASK="assemble${BUILD_TYPE_CAP}"

echo "=== Building 米控 $VERSION for $BUILD_TYPE ==="

# Validate signing env vars for release
if [[ "$BUILD_TYPE" == "release" ]]; then
    if [[ -z "$KEYSTORE" ]]; then
        echo "ERROR: KEY_STORE_LOCATION env var not set"
        exit 1
    fi
    if [[ -z "$KEY_ALIAS" ]]; then
        echo "ERROR: KEY_ALIAS env var not set"
        exit 1
    fi
    if [[ -z "$KEYSTORE_PASS" ]]; then
        echo "ERROR: KEY_STORE_PASSWORD env var not set"
        exit 1
    fi
fi

# Build
cd "$PROJECT_DIR"
./gradlew "$GRADLE_TASK"

# Find APK
BUILD_DIR="$APP_DIR/build/outputs/apk/$BUILD_TYPE"
APK_FILE=""
if [[ "$BUILD_TYPE" == "release" ]]; then
    # Gradle signingConfig produces signed APK directly
    APK_FILE="$BUILD_DIR/app-release.apk"
else
    APK_FILE="$BUILD_DIR/app-debug.apk"
fi

if [[ ! -f "$APK_FILE" ]]; then
    echo "ERROR: APK not found at $APK_FILE"
    ls "$BUILD_DIR" 2>/dev/null || true
    exit 1
fi

# Copy to project root with versioned name
SIGNED_APK=""
if [[ "$BUILD_TYPE" == "release" ]]; then
    SIGNED_APK="$PROJECT_DIR/MiLocalControl-${VERSION}-signed.apk"
    cp -f "$APK_FILE" "$SIGNED_APK"
else
    SIGNED_APK="$PROJECT_DIR/MiLocalControl-${VERSION}-debug.apk"
    cp -f "$APK_FILE" "$SIGNED_APK"
fi

SIZE=$(du -h "$SIGNED_APK" | cut -f1)
echo "=== Done: $SIGNED_APK ($SIZE) ==="
