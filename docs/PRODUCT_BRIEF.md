# Product Brief: NovelNest

## 1. Problem Statement
Web novel and manhwa readers struggle with cluttered, ad-heavy websites, slow page loads, disjointed reading histories, and having to switch between multiple platforms (NovelFire, FreeWebNovel, Asura Scans). NovelNest solves this by offering a clean, calm, ad-free native Android reading environment with unified discovery, curated recommendations, and instant source-filtered search.

## 2. Target Users & Devices
- **Users**: Enthusiasts of light novels, web novels, xianxia, progression fantasy, and manhwa.
- **Devices**: Android phones and tablets running Android 8.0 (API 26) through Android 15/16 (API 35+).
- **Aesthetic Expectation**: Calm, soothing, distraction-free typography and dark/warm tones that reduce eye strain during extended reading sessions.

## 3. Core Flow
1. Open the app to the **Home** or **Discover** screen.
2. Select or swipe between sources (**NovelPhoenix**, **FWN (FreeWebNovel)**, **Asura Scans**).
3. Browse hero recommendation slides and categorized sections (Top Trending, Latest Releases, Popular).
4. Tap the Search bar with fluid transition animation to search specifically within the active source.
5. View novel posters, genres, ratings, and chapter counts.

## 4. MVP Scope
### Included in Version 1:
- Single-activity Jetpack Compose application with Material 3 calm reading theme.
- Tab-based multi-source browsing:
  - **NovelPhoenix**
  - **FreeWebNovel (FWN)**
  - **Asura Scans**
- Hero recommendation carousel with smooth indicator and auto/manual scroll.
- Section feeds (Top Trending, Latest Updates, Most Popular) with rich novel posters and metadata.
- Animated Search transition: tapping the search bar seamlessly expands into full source-scoped search.
- Clean Repository pattern with remote data sources (Jsoup/OkHttp web parsing + graceful fallback when offline/blocked).
- State-driven architecture (`UiState`, `ViewModel`, Unidirectional Data Flow).
- Error, loading shimmer, empty, and offline resilience states.

### Deferred to Version 2:
- Full offline chapter downloader & background synchronization via WorkManager.
- Account sync & cloud bookmarks.
- Comments and social reviews.

## 5. Data & Privacy
- **Data Collected**: Zero personal data collected or transmitted.
- **Local Storage**: Reading history, bookmarks, and search queries stored locally on-device.
- **Retention**: Fully managed by user with clear-all capability.

## 6. Network & Offline Behavior
- Network calls handled via OkHttp and Jsoup for HTML parsing with custom User-Agent headers.
- When network is unavailable or remote sites throttle, the app gracefully falls back to cached data / robust offline placeholders and informs the user with retry options.

## 7. Permissions
- `android.permission.INTERNET`: Required to fetch novel directory content and poster artwork.
- `android.permission.ACCESS_NETWORK_STATE`: Required to monitor connectivity and adapt UI states.
- No sensitive permissions (contacts, storage, camera, location) requested.

## 8. Success Criteria
- Home and Discover tabs load smoothly with hero recommendation carousels and section feeds for all 3 sources.
- Animated search transitions cleanly and filters novels by keyword within the active tab.
- UI renders cleanly with high contrast, calm dark/warm aesthetic, and no main thread stutter.
