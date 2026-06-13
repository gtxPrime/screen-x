<div align="center">

# ScreenX

**Pure, powerful screen recording for Android. No limits, no nonsense.**

  <p>
    <a href="https://github.com/gtxprime/screen-x/stargazers">
      <img src="https://img.shields.io/github/stars/gtxprime/screen-x?style=for-the-badge&color=yellow" alt="Stars" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/network/members">
      <img src="https://img.shields.io/github/forks/gtxprime/screen-x?style=for-the-badge&color=orange" alt="Forks" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/issues">
      <img src="https://img.shields.io/github/issues/gtxprime/screen-x?style=for-the-badge&color=blue" alt="Issues" />
    </a>
    <a href="https://github.com/gtxprime/screen-x/blob/main/LICENSE">
      <img src="https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge" alt="License" />
    </a>
    <a href="#">
      <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=for-the-badge" alt="Platform" />
    </a>
  </p>

  <h3>
    <a href="#-features">Features</a>
    <span> | </span>
    <a href="#-tech-stack">Tech Stack</a>
    <span> | </span>
    <a href="#-project-structure">Project Structure</a>
    <span> | </span>
    <a href="#-installation">Installation</a>
    <span> | </span>
    <a href="#-contributing">Contributing</a>
  </h3>

</div>

---

## 📱 About ScreenX

**ScreenX** is a modern, high-performance screen recording utility built for Android. It prioritizes smooth performance, minimal system overhead, and useful productivity features like live annotations and floating overlays. Whether you're recording gameplay, creating app walkthroughs, or capturing bug reports, ScreenX handles it with style.

---

## 🚀 Features

### 🎥 High-Fidelity Recording
* **Custom Configurations:** Adjust video resolution (up to 1080p+), frame rates (30 FPS, 60 FPS), and video/audio bitrates to balance quality and file size.
* **Format Control:** Generates highly compatible `.mp4` video files using Android's hardware-accelerated MediaCodec API.
* **Dynamic Orientation:** Automatically adapts recording orientation based on your device state.

### 🎙️ Multi-Channel Audio Capture
* **Audio Sources:** Record clear external microphone audio, internal system audio (Android 10+), or both simultaneously.
* **Custom Quality:** Configure sample rates and audio bitrates for crystal-clear sound.

### 🖌️ Live Annotations & Brush Tools
* **Draw on Screen:** Activate the drawing overlay to annotate, circle, or write directly on top of active apps while recording.
* **Custom Styling:** Choose from standard brush colors (Red, Green, Blue, Yellow) and adjust brush size dynamically.
* **Quick Clear:** Easily erase strokes or clear the entire canvas with a single tap from the overlay controls.

### 🎛️ Floating Control Panel
* **Quick Access:** A non-intrusive overlay button that expands into standard record/pause/stop and brush tools.
* **Smart Placement:** Drag-and-drop floating widget that snaps to the edges of the screen and remembers its position.
* **Auto-Hide:** Automatically hides itself during inactive periods or shifts visibility according to settings.

### ⚡ Quick Settings Tile Integration
* **One-Tap Recording:** Initiate or stop screen recordings instantly directly from the Android Quick Settings panel without opening the app UI.
* **Seamless Launching:** Handles foreground service and media projection requests seamlessly in the background.

---

## 🛠️ Tech Stack & Architecture

ScreenX is designed with modern Android development practices, ensuring scalability, performance, and clean code division:

* **Language:** 100% Kotlin
* **UI Framework:** Jetpack Compose with Material Design 3 (Material You dynamic theme support)
* **Background Tasks:** Android Foreground Services (`ScreenRecordService`) with high-priority Notification channels
* **Media Pipelines:** MediaProjection API, MediaRecorder, and custom AudioPlaybackCapture configurations
* **State Management:** Kotlin Coroutines and Flows for reactive settings management
* **Data Layer:** Jetpack DataStore / SharedPreferences for storing user configurations

---

## 📂 Project Structure

```
d:\ScreenX
│
├── app/src/main/java/com/screenx/recorder/
│   ├── data/
│   │   └── SettingsManager.kt       # Manages recording and UI settings
│   │
│   ├── service/
│   │   ├── ScreenRecordService.kt   # Core background recording service
│   │   ├── AudioCaptureHelper.kt    # Logic for internal / microphone audio capture
│   │   ├── FloatingControlOverlay.kt # Draggable overlay control panel
│   │   ├── BrushDrawingOverlay.kt   # Canvas overlay for drawing on screen
│   │   ├── CountdownOverlay.kt      # Initial countdown overlay before recording
│   │   ├── ScreenXTileService.kt    # Quick Settings Tile service
│   │   └── TileHelperActivity.kt    # Invisible activity helper for tile launches
│   │
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt        # Home UI with video list & main controls
│   │   │   └── SettingsScreen.kt    # Recording and styling preferences UI
│   │   └── theme/
│   │       ├── Color.kt             # Material 3 theme colors
│   │       ├── Theme.kt             # Application theme initialization
│   │       └── Type.kt              # Font and typography settings
│   │
│   ├── utils/
│   │   └── VideoHelper.kt           # Utilities for video file queries and deletions
│   │
│   └── MainActivity.kt              # Entry point activity handling permissions & navigation
```

---

## ⚙️ Installation & Development Setup

### Prerequisites
* Android Studio (Ladybug or newer recommended)
* Android SDK 26 (Android 8.0) or higher
* Java Development Kit (JDK) 17

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/gtxprime/screen-x.git
   cd screen-x
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run the app on a connected physical device or emulator.

---

## 🤝 Contributing

Contributions are welcome! If you find bugs, have feature requests, or want to enhance ScreenX:
1. **Fork** the repository.
2. **Create a branch** for your feature/bug fix (`git checkout -b feature/amazing-feature`).
3. **Commit** your changes (`git commit -m 'Add amazing feature'`).
4. **Push** to the branch (`git push origin feature/amazing-feature`).
5. **Open a Pull Request**.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

---

## 📈 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=gtxprime/screen-x&type=Date)](https://star-history.com/#gtxprime/screen-x&Date)

---

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
