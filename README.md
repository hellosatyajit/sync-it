# Sync It

Sync It is a personal, end-to-end encrypted clipboard bridge for Android and macOS. Text and images travel through a small relay, but the relay only sees encrypted bytes.

## What is included

- `macos/` — a native macOS menu-bar app. It watches the Mac clipboard, sends new text/images, and applies clipboard items received from Android.
- `android/` — a native Android app. It syncs automatically while visible and exposes a persistent **Send clipboard** notification for background use (required by Android's clipboard privacy rules).
- `relay/` — a zero-dependency Node.js relay that makes syncing work when the devices are on different networks.

## Quick start

### 1. Run the relay

```bash
cd relay
npm test
npm start
```

For use outside your home network, deploy `relay/` to any Node host with HTTPS (Fly.io, Railway, Render, a VPS, etc.). Set `SYNCIT_DATA_FILE` to a persistent path if desired. The server listens on `PORT` (default `8787`).

### 2. Run the Mac app

```bash
cd macos
./build-app.sh
open "dist/Sync It.app"
```

Open **Settings…** from the menu-bar icon and enter the HTTPS relay URL and the same pairing phrase used on Android. The script creates an ad-hoc signed app for this Mac. To distribute it to other people, sign and notarize it with an Apple Developer identity in Xcode.

### 3. Run the Android app

Open `android/` in Android Studio, run it on the phone, then enter the relay URL and pairing phrase. Tap **Start sync**. While Android shows the app, clipboard changes are sent automatically. When it is in the background, tap **Send clipboard** in the persistent notification.

To build an installable debug APK from the terminal, use Java 17:

```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

Android 13+ may ask for notification permission. Images copied by some apps are exposed as private content URIs; Sync It reads them immediately when allowed, but a source app can still deny access.

You can also send screenshots, images, links, or selected text through Android's Share Sheet: tap **Share**, choose **Send to Mac**, and Sync It sends the item directly to the Mac clipboard.

## Pairing and privacy

Use a long, unique phrase (four or more random words). The phrase never leaves either device. Each client derives:

- a channel ID using SHA-256, used only to route messages;
- an AES-256-GCM key using PBKDF2-HMAC-SHA256 (120,000 rounds), used to encrypt the clipboard payload.

The relay stores only the channel ID and encrypted envelopes. It retains at most 50 messages per channel for 24 hours. Clipboard size is capped at 8 MiB after encoding.

## Production notes

- Use HTTPS. The apps reject cleartext internet traffic by default.
- Run a single relay instance unless you replace the JSON-file store with shared storage.
- Anyone who knows the pairing phrase can join the channel; choose it like a password.
- There is no account system because this project is designed for one person and their own devices.
