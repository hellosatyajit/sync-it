#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"
swift build -c release

APP="$SCRIPT_DIR/dist/Sync It.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$SCRIPT_DIR/.build/release/SyncItMac" "$APP/Contents/MacOS/SyncItMac"
cp "$SCRIPT_DIR/Info.plist" "$APP/Contents/Info.plist"
codesign --force --sign - "$APP"
echo "$APP"
