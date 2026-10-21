# NovelNest 📚

A modern, distraction-free native Android novel and manhwa reading application built with **Kotlin** and **Jetpack Compose**, adhering strictly to the architecture and engineering principles defined in [kotlin_android_app_rulebook.md](kotlin_android_app_rulebook.md).

---

## ✨ Features Implemented

1. **Multi-Source Architecture**:
   - **NovelFire (`novelfire.net`)**: Web & Light novels with high-rated progression fantasy, xianxia, and litRPG.
   - **FreeWebNovel / FWN (`freewebnovel.com`)**: Classic and trending web novels.
   - **Asura Scans (`asuracomic.net`)**: Action, murim, fantasy, and solo hunter manhwa.

2. **Calm & Professional Reading UI/UX**:
   - Curated serene dark palette designed for extended reading without eye fatigue (warm honey amber `#E2A765`, deep obsidian slate `#0F1216`, soft ivory typography `#F4F0E6`, and sage green accents).
   - Material 3 design tokens with edge-to-edge support.
   - Fluid typography and card elevations with subtle borders.

3. **Curated Home & Discover Tabs**:
   - **Featured Hero Recommendation Carousel**: Auto-advancing/swipeable carousel with poster backdrop, dark gradient overlay, rating stars, read counts, and quick "Read" action.
   - **Categorized Sections**: "Featured Masterpieces", "Top Trending", "Latest Releases", "Fresh Updates" with smooth horizontal scrolling.
   - **Discover Grid**: Genre pills (Action, Fantasy, Xianxia, Mystery, etc.) and responsive 2-column catalog grid.
   - **Novel Quick Preview Sheet**: Bottom sheet with cover artwork, rating, author, synopsis, genre chips, and start reading button.

4. **Fluid Animated Search Transition**:
   - Tapping the search bar on Home or Discover smoothly animates into an immersive dedicated Search view.
   - Scoped to the currently selected source tab with instant tab-switching directly inside Search.
   - Real-time debounced query execution (350ms) to prevent network spam.
   - Quick search chips for popular and recent terms.

5. **Resilient Data & Scraper Layer**:
   - Decoupled `NovelSource` interface implemented for NovelFire, FreeWebNovel, and Asura Scans using OkHttp & Jsoup.
   - Memory caching in `NovelRepositoryImpl` for instant tab switching.
   - Graceful offline/timeout fallback to rich seed data so the user experience never breaks if remote sites are slow, offline, or anti-bot throttled.

---

## 🏛️ Architecture & Rulebook Compliance

Following `kotlin_android_app_rulebook.md`:
- **Unidirectional Data Flow (UDF)**: ViewModels expose observable `StateFlow<UiState>`. Composables emit events up and receive immutable state down.
- **Single Source of Truth**: All UI rendering depends strictly on immutable UI states (`HomeUiState`, `DiscoverUiState`, `SearchUiState`).
- **Clean Boundaries**: Screens never talk directly to HTTP clients or parsers; all requests pass through `NovelRepository`.
- **Lean Single-Module Structure**: Uses a clean, scalable package structure (`core/`, `data/`, `feature/`, `navigation/`).
- **Product & Architecture Documentation**: Documented in [`docs/PRODUCT_BRIEF.md`](docs/PRODUCT_BRIEF.md) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## 🚀 How to Open and Run in Android Studio

1. **Android Studio Location**:
   Your Android Studio is installed at:
   ```text
   E:\Android\Android Studio
   ```
2. **Android SDK Location**:
   Configured in `local.properties`:
   ```text
   sdk.dir=E\:/Android/Sdk
   ```
3. **Open Project**:
   - Launch Android Studio.
   - Click **Open** and select `E:\novel-nest`.
   - Android Studio will automatically sync Gradle using the configured Gradle 8.14.3 wrapper and AGP 8.7.3.
4. **Run on Emulator / Device**:
   - Select an emulator or connected physical Android device.
   - Click the green **Run (Shift + F10)** button.
