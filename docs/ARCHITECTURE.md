# Architecture: NovelNest

## Architectural Principles (Following Android & Rulebook Guidance)

1. **Unidirectional Data Flow (UDF)**: Events propagate upwards to ViewModels; immutable `UiState` flows down to Jetpack Compose screens.
2. **Layer Separation**:
   - **UI Layer**: Composable screens (`HomeScreen`, `DiscoverScreen`, `SearchScreen`), custom design system tokens, and theme.
   - **State Layer**: Android ViewModels managing screen-level lifecycle and exposing `StateFlow<UiState>`.
   - **Domain / Repository Layer**: `NovelRepository` abstraction coordinating data retrieval without exposing network/parsing details to UI.
   - **Remote Data Sources**: Clean source-specific scrapers/clients (`NovelFireSource`, `FreeWebNovelSource`, `AsuraSource`) implementing common `NovelSource` interface.
3. **Resilience & Testing**:
   - Network errors, timeouts, and empty responses mapped to explicit sealed UI states.
   - Fallback offline sample data available for deterministic unit testing and previewing when offline.

## Project Structure

```text
app/
├── src/main/
│   ├── java/com/novelnest/app/
│   │   ├── MainActivity.kt
│   │   ├── NovelNestApp.kt
│   │   ├── core/
│   │   │   ├── designsystem/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   └── ui/
│   │   │       ├── NovelCard.kt
│   │   │       ├── HeroCarousel.kt
│   │   │       ├── SourceTabRow.kt
│   │   │       └── SearchBarView.kt
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   ├── Novel.kt
│   │   │   │   ├── NovelSourceType.kt
│   │   │   │   └── NovelSection.kt
│   │   │   ├── remote/
│   │   │   │   ├── NovelSource.kt
│   │   │   │   ├── NovelFireSource.kt
│   │   │   │   ├── FreeWebNovelSource.kt
│   │   │   │   └── AsuraSource.kt
│   │   │   └── repository/
│   │   │       ├── NovelRepository.kt
│   │   │       └── NovelRepositoryImpl.kt
│   │   ├── feature/
│   │   │   ├── home/
│   │   │   │   ├── HomeScreen.kt
│   │   │   │   ├── HomeViewModel.kt
│   │   │   │   └── HomeUiState.kt
│   │   │   ├── discover/
│   │   │   │   ├── DiscoverScreen.kt
│   │   │   │   ├── DiscoverViewModel.kt
│   │   │   │   └── DiscoverUiState.kt
│   │   │   └── search/
│   │   │       ├── SearchScreen.kt
│   │   │       ├── SearchViewModel.kt
│   │   │       └── SearchUiState.kt
│   │   └── navigation/
│   │       ├── NovelNestDestination.kt
│   │       └── NovelNestNavHost.kt
│   └── AndroidManifest.xml
├── build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
```
