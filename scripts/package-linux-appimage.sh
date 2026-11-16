#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/composeApp/build/compose/binaries/main/app/github-mobile-maxxing"
ICON_FILE="$ROOT_DIR/composeApp/src/desktopMain/resources/app-icon.png"
WORK_DIR="$ROOT_DIR/composeApp/build/compose/appimage"
APPDIR="$WORK_DIR/AppDir"
OUT_DIR="$ROOT_DIR/composeApp/build/compose/binaries/main/appimage"
OUT_FILE="$OUT_DIR/github-mobile-maxxing-x86_64.AppImage"

if [ ! -x "$DIST_DIR/bin/github-mobile-maxxing" ]; then
    echo "Distributable not found at $DIST_DIR; run :composeApp:createDistributable first." >&2
    exit 1
fi

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr" "$OUT_DIR"

cp -a "$DIST_DIR/." "$APPDIR/usr/"
cp "$ICON_FILE" "$APPDIR/github-mobile-maxxing.png"
cp "$ICON_FILE" "$APPDIR/.DirIcon"

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/github-mobile-maxxing" "$@"
EOF
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/github-mobile-maxxing.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=github-mobile-maxxing
Comment=Fast Material 3 Expressive GitHub Client
Exec=github-mobile-maxxing
Icon=github-mobile-maxxing
Categories=Development;Utility;
Terminal=false
EOF

APPIMAGETOOL_BIN="$(command -v appimagetool || true)"
if [ -z "$APPIMAGETOOL_BIN" ]; then
    CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/appimagetool"
    mkdir -p "$CACHE_DIR"
    APPIMAGETOOL_BIN="$CACHE_DIR/appimagetool-x86_64.AppImage"
    if [ ! -x "$APPIMAGETOOL_BIN" ]; then
        echo "Downloading appimagetool..."
        curl -fSL \
            "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" \
            -o "$APPIMAGETOOL_BIN"
        chmod +x "$APPIMAGETOOL_BIN"
    fi
fi

ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$APPIMAGETOOL_BIN" --no-appstream "$APPDIR" "$OUT_FILE"
chmod +x "$OUT_FILE"
echo "Generated Linux AppImage: $OUT_FILE"
