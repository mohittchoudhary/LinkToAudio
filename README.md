# 🎵 LinkToAudio

**LinkToAudio** is a native Android application built with Kotlin and Jetpack Compose (Material 3) that downloads and converts any web link or playlist—including YouTube, Spotify, SoundCloud, Instagram, TikTok, Facebook, Twitter/X, Bandcamp, and Vimeo—directly into high-quality audio files (`.mp3` or `.m4a`) on your phone.

Powered by `yt-dlp` and `ffmpeg` running entirely on-device via `youtubedl-android`, it requires **no backend server**, preserves privacy, and saves converted audio directly into your phone's standard `Music/LinkToAudio` (and `Music/LinkToAudio/<Playlist>/`) directories.

---

## ✨ Features

- **🌐 Universal Any-Link Support:**
  - Converts audio from YouTube, Spotify, SoundCloud, Instagram, TikTok, Twitter/X, Facebook, Bandcamp, Vimeo, Dailymotion, and any platform supported by `yt-dlp`.
- **⚡ Auto-Convert on Share (Core Feature):**
  - Tap **Share** on any link in any app and select **LinkToAudio**.
  - The app automatically extracts the link and starts converting immediately with zero extra taps (when auto-convert on share is enabled).
  - Background foreground service with a live progress notification.
- **📑 Full Playlist & Album Support:**
  - Detects playlists (YouTube, SoundCloud, Spotify albums/playlists, etc.) automatically.
  - Interactive playlist screen: select/deselect tracks, "Select all / none", and convert chosen tracks.
  - Sequential queue with progress indicators ("Track 3 of 25: Title").
  - Saves playlists into dedicated folders (`Music/LinkToAudio/<Playlist Name>/`).
  - Graceful error handling: skips failed/geo-blocked tracks and reports summary at completion.
- **🎧 Spotify Links:**
  - Resolves tracks, albums, and playlists via public embed pages without requiring user login.
  - Matches and converts best audio stream via `ytsearch1:` on YouTube.
  - Embeds metadata and cover art thumbnails.
  - Optional Spotify Web API client-credentials fallback in Settings.
- **🎛️ Audio Quality & Format Selection:**
  - Choose audio quality: **Best**, **192 kbps**, or **128 kbps**.
  - Formats: **MP3** (universal) and **M4A** (crisp AAC).
- **⏭️ Skip Already Downloaded:**
  - Optional toggle to skip tracks that already exist in your conversion history.
- **📜 Conversion History:**
  - Built-in history screen listing all past converted tracks. Play, share, or delete any track at any time.
- **🔄 On-Device Engine Updater:**
  - A dedicated **"Update Engine"** button in Settings that fetches the latest `yt-dlp` release directly to the phone.
- **🛡️ Clean & Compliant Storage:**
  - Uses modern Android MediaStore API (API 29–34), saving into `Music/LinkToAudio` and playlist subfolders.

---

## 🚀 How to Build & Download the APK (No Android Studio Needed!)

You don't need Android Studio or any developer tools installed on your computer. You can build the APK in the cloud using **GitHub Actions** and download it directly to your phone.

### Step 1: Create a GitHub Repository
1. Log in to [GitHub](https://github.com/).
2. In the top-right corner, click the **`+`** icon and select **New repository**.
3. Name your repository (e.g., `LinkToAudio`).
4. You can set it to **Public** or **Private** (GitHub Actions is free for both).
5. Do **not** check "Add a README" or ".gitignore" (the project already has them).
6. Click **Create repository**.

### Step 2: Push the Code to GitHub
Open your computer's terminal / PowerShell in this project folder and run the following commands:

```bash
# 1. Initialize git (if not already done)
git init

# 2. Stage all files (including the Gradle wrapper and workflow)
git add .

# 3. Create initial commit
git commit -m "Initial commit of LinkToAudio"

# 4. Set branch name to main
git branch -M main

# 5. Connect to your GitHub repository (replace with your GitHub URL)
git remote add origin https://github.com/<YOUR-USERNAME>/<YOUR-REPO-NAME>.git

# 6. Push code to GitHub
git push -u origin main
```

### Step 3: Download Your APK from GitHub Actions
1. On your GitHub repository page, click the **Actions** tab at the top.
2. You will see a workflow named **"Build APK"** running automatically.
3. Click on the running workflow to monitor the build progress (it usually takes 2–4 minutes).
4. Once the build shows a green checkmark (**Passed**), click on the workflow run.
5. Scroll down to the **Artifacts** section at the bottom of the summary page.
6. Click **`LinkToAudio-apk`** to download the zip file.
7. Unzip the file to find **`app-debug.apk`**.

### Step 4: Install the APK on Your Android Phone
1. Transfer `app-debug.apk` to your phone (via USB cable, Google Drive, Telegram "Saved Messages", or download directly from GitHub in your phone's browser).
2. Tap on `app-debug.apk` on your phone to install.
3. If Android prompts **"Install unknown apps"** or **"For your security, your phone is not allowed to install unknown apps from this source"**:
   - Tap **Settings** on the prompt.
   - Toggle **Allow from this source** to **ON**.
   - Go back and tap **Install**.
4. Tap **Open** and enjoy LinkToAudio!

---

## 🛠️ (Optional) Building Locally with Command-Line Tools

If you have JDK 17 and the Android Command-Line SDK installed locally:

### Prerequisites:
- **Java Development Kit (JDK):** Version 17 (e.g. Eclipse Temurin 17 or OpenJDK 17).
- **Android SDK:** Command-line tools with API 34 platform and build-tools installed.
- Environment variables:
  - `JAVA_HOME` pointing to JDK 17.
  - `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing to your Android SDK directory.

### Build Command:

**On Windows (PowerShell or Command Prompt):**
```powershell
.\gradlew.bat assembleDebug
```

**On Linux / macOS:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

The output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

You can install it directly to a connected phone using ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 How to Use "Auto-Convert on Share"

1. Open the **YouTube** or **Instagram** app (or a web browser).
2. Find any video or Reel you want as audio.
3. Tap the **Share** button.
4. Select **LinkToAudio** from the list of apps.
5. **That's it!** LinkToAudio immediately starts converting in the background. A notification will display live download progress.
6. When complete, tap the **"Audio saved"** notification or open LinkToAudio to play or share the audio file.

---

## ⚠️ Disclaimer

This application is intended for personal archiving and offline listening of content that you own or have explicit permission to download. Respect copyright and platform terms of service.
