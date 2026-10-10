# ScreenX Release Notes — v1.4.0

## 🚀 What's New & Enhancements

* **🔄 In-App Update Checker (via GitHub Releases)**
  * Added direct integration with GitHub's Releases API to check for new app versions.
  * Added **"Check for Updates"** button and an **"Auto-Check for Updates"** toggle in Settings.
  * Custom themed update dialog matching the ScreenX native dark & expressive design system with release notes preview and direct APK download links.

* **🎛️ Swap Main Card on Home Screen**
  * Added a new setting under **Stealth Recording** allowing users to switch the big AGSL shader hero card to **Stealth Recording** and the secondary row to **Standard Recording**.
  * Seamlessly swaps functionality, icons, status badges, and trigger actions while preserving the exact layout, shader ripple effects, and visual styling.

* **📴 Stop Recording on Screen Off**
  * Added a **"Stop on Screen Off"** toggle in Settings.
  * Automatically detects device lock / screen turn-off events (`ACTION_SCREEN_OFF`) and cleanly finalizes active standard or stealth recordings without file corruption.

* **👻 Hide Floating Ball While Recording**
  * Added a **"Hide Ball While Recording"** option in Settings.
  * Automatically hides the floating control overlay during an active recording session and restores it upon pause or stop.

* **⚡ Quick Settings Tile Enhancements**
  * Fixed tile visibility across various Android OEM skins by ensuring `android:enabled="true"` and proper `ACTIVE_TILE` metadata.
  * Added a one-tap **"Add Quick Settings Tile"** shortcut in Settings (utilizing `StatusBarManager.requestAddTileService` on Android 13+).

---

## 🐛 Bug Fixes & Stability

* **Fixed Stealth Recording Premature Stop on App Switch**:
  * Resolved an issue where switching from ScreenX to another app caused native `/system/bin/screenrecord` to exit due to display surface changes.
  * Added explicit display resolution locking (`--size WIDTHxHEIGHT`) to match the hardware screen metrics.
  * Implemented session continuity and strict user stop tracking so window transitions settle without dropping the recording session or prematurely saving short clips.
* **Fixed Wireless ADB Pairing Dialog Listener**:
  * Ensured `PairingInputService` stops actively listening as soon as pairing is validated and confirmed, preventing lingering notifications.
* **Streamlined Toast Notifications**:
  * Cleaned up notifications to show straightforward `"Recording started"` and `"Recording stopped"` alerts across both standard and stealth recording modes.

---

## 📦 Artifact Details
* **File**: `app-release.apk`
* **Version**: `v1.4.0` (Build `5`)
* **Package**: `com.gxdevs.screenx`
* **Signer**: Verified Release Key
