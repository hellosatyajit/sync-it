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

### Optional: use it from Raycast

Add this repository's `raycast/` directory under **Raycast → Extensions → Script Commands → Add Directories**. Raycast will expose **Send Clipboard with Sync It** and **Open Sync It Settings**. The Mac app must be installed/opened once so macOS registers its `syncit://` actions.

### 3. Run the Android app

Open `android/` in Android Studio, run it on the phone, then enter the relay URL and pairing phrase. Tap **Start sync**. While Android shows the app, clipboard changes are sent automatically. When it is in the background, tap **Send clipboard** in the persistent notification.

Android 13+ may ask for notification permission. Images copied by some apps are exposed as private content URIs; Sync It reads them immediately when allowed, but a source app can still deny access.

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
