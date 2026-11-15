# OctoExpress

A fast, custom **GitHub client** built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**, targeting **Android** and **Linux Desktop**, powered by **Material 3 Expressive (MD3E)**.

## Features
- **OAuth Device Flow + PAT Fallback**: Zero-secret GitHub OAuth Device Flow (`https://github.com/login/device/code`) with automatic browser launch, one-click code copy, adaptive polling (`slow_down` / `authorization_pending`), and Personal Access Token fallback.
- **Platform-Native Secure Token Storage**:
  - **Android**: Hardware-backed Android Keystore AES-256-GCM encrypted session storage.
  - **Linux**: Freedesktop Secret Service (`secret-tool` / GNOME Keyring / KDE KWallet) with owner-only (`0600`) XDG Data fallback.
- **Hybrid GitHub GraphQL v4 + REST v3 API**:
  - **Inbox (Notifications)**: Swipe-to-done / mark-read triage, Unread/Participating/All filter, live adaptive split-pane Issue/PR preview.
  - **Issues & Pull Requests**: Single-query GraphQL timeline (Markdown comments, reviews, commits, state events), inline unified Diff viewer with line numbers, PR Review submission (`Approve` / `Request Changes` / `Comment`), and Merge `SplitButton` (`Squash` / `Merge Commit` / `Rebase`).
  - **Repositories & Code Browser**: My Repos, Starred, Search, Branch switcher, File Tree + syntax-highlighted Code Viewer, README Markdown rendering, Star/Unstar, and New Issue dialog.
- **Material 3 Expressive (MD3E)**:
  - `MaterialExpressiveTheme` + `MotionScheme.expressive()` + MaterialKolor `SPEC_2025` dynamic palettes + Android 12+ Wallpaper Dynamic Color.
  - `ContainedLoadingIndicator` / `LoadingIndicator`, `LinearWavyProgressIndicator`, `HorizontalFloatingToolbar`, `SplitButtonLayout`, connected `ButtonGroup` (`ToggleButton`), and adaptive `NavigationSuiteScaffold` + `ListDetailPaneScaffold`.

## Setup

### 1. GitHub OAuth Client ID (Recommended)
1. Go to **GitHub Settings → Developer settings → OAuth Apps → New OAuth App** (or GitHub Apps).
2. Enable **Device Flow** in the app settings.
3. Either:
   - Add `github.client.id=YOUR_CLIENT_ID` to `local.properties` to bake it in as the default, **or**
   - Enter your Client ID directly on the Sign-In screen at runtime.

### 2. Build & Run

#### Linux Desktop
```bash
./gradlew :composeApp:run
```
Create a Linux distributable (`AppImage` / `.deb` / runnable bundle):
```bash
./gradlew :composeApp:createDistributable
```

#### Android
For full 120Hz / R8-optimized performance, build the release APK (signed with the debug keystore for direct local `adb install`):
```bash
./gradlew :androidApp:assembleRelease
```
Output APK: `androidApp/build/outputs/apk/release/androidApp-release.apk`.

Debug build (unminified, useful for profiling/debugging):
```bash
./gradlew :androidApp:assembleDebug
```
Output APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

#### Tests
```bash
./gradlew :core:model:desktopTest :core:auth:desktopTest :core:network:desktopTest
```
