# Kotlin Android App Rulebook
## A practical guide to planning, structuring, building, and protecting an Android app

**Purpose:** Use this as a repeatable checklist when starting a new native Android application with Kotlin. It is based on current Android architecture guidance and a focused source-code review of Google's public `android/nowinandroid` and `android/architecture-samples` repositories. It is not a claim that every Android app on GitHub was reviewed; the ecosystem is too large for an exhaustive audit, and repository quality varies.

---

## 1. The most important rule

**Do not start by building every screen. Start by defining the smallest useful version of the app and proving its core flow works.**

Before coding, write down:

1. **Problem:** What real user problem does this app solve?
2. **Users:** Who will use it, and on what devices or Android versions?
3. **Core flow:** What is the one task a user must be able to complete?
4. **MVP scope:** What is required for version 1, and what can wait?
5. **Data:** What data is collected, where it comes from, where it is stored, and how long it is retained.
6. **Network:** Does the app need a backend? What happens offline or when requests fail?
7. **Permissions:** Which Android permissions are truly necessary, and at what point should they be requested?
8. **Success criteria:** How will you know the first version works (for example, task completed, book saved, or audio resumed correctly)?

Keep these decisions in a short `docs/PRODUCT_BRIEF.md`. If you cannot explain the core flow in a few sentences, do not begin with a large architecture.

## 2. Research reviewed and what it teaches

### A. Google `android/nowinandroid`
Repository: https://github.com/android/nowinandroid

What was reviewed:
- `README.md`
- `docs/ArchitectureLearningJourney.md`
- `docs/ModularizationLearningJourney.md`
- `settings.gradle.kts`
- `core/data/.../OfflineFirstTopicsRepository.kt`

Observed lessons:
- It uses Kotlin and Jetpack Compose and follows Android's published architecture guidance.
- It separates UI, data, and domain responsibilities and uses unidirectional data flow (UDF): user events move down; state/data moves up.
- `OfflineFirstTopicsRepository` reads topic data from Room-backed local storage as a `Flow`, while synchronization updates local storage from the network. This makes the database the UI's normal read source and supports offline use.
- Its `settings.gradle.kts` includes many feature, core, test, sync, and benchmark modules. That level of modularization supports a substantial app and team, but it is not a sensible default for every beginner project.
- Its `build-logic` convention plugins reduce duplicated Gradle configuration across modules.

**Rule learned:** Copy the separation of responsibilities and the data-flow ideas; do not copy the entire module count just because a large official app uses it.

### B. Google `android/architecture-samples`
Repository: https://github.com/android/architecture-samples

What was reviewed:
- Project README
- `TasksViewModel.kt` source

Observed lessons:
- The current branch demonstrates a single-activity app with Navigation Compose, a Compose screen and ViewModel per screen/feature, Kotlin Flow/coroutines, a repository with local and fake remote data sources, Hilt, and multiple test types.
- `TasksViewModel` exposes a single `TasksUiState` stream assembled from flows, tracks loading/error/user-message state, and uses `viewModelScope` for work. The UI can render state instead of owning business logic.
- The sample is deliberately a teaching project. Its README says it is not a production template; it does not provide a real production backend/authentication system.

**Rule learned:** Make screens state-driven and testable. Use fake data for early development, but explicitly test real network and persistence behavior before release.

### C. Android Developers architecture recommendations
https://developer.android.com/topic/architecture/recommendations

Main guidance reflected in this rulebook:
- Clearly separate UI and data layers.
- Expose app data through repositories; UI and ViewModels should not access Room, DataStore, HTTP clients, or platform data sources directly.
- Use coroutines and Flow for asynchronous work and streams.
- Add a domain/use-case layer when business logic is reused or complex enough to justify it; it is not mandatory in every small app.
- Keep a single source of truth and use unidirectional data flow.

---

## 3. Recommended starting architecture

For a small-to-medium app, start with **one app module** and packages organized by responsibility or feature. Add Gradle modules only when there is a concrete reason.

```text
MyApp/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/myapp/
│       │   │   ├── MainActivity.kt
│       │   │   ├── App.kt
│       │   │   ├── navigation/
│       │   │   │   └── AppNavHost.kt
│       │   │   ├── core/
│       │   │   │   ├── ui/              # shared UI components/theme
│       │   │   │   ├── common/          # small shared utilities
│       │   │   │   └── designsystem/    # only if design system is needed
│       │   │   ├── feature/
│       │   │   │   ├── home/
│       │   │   │   │   ├── HomeScreen.kt
│       │   │   │   │   ├── HomeViewModel.kt
│       │   │   │   │   └── HomeUiState.kt
│       │   │   │   └── settings/
│       │   │   ├── data/
│       │   │   │   ├── repository/
│       │   │   │   ├── local/            # Room/DataStore implementations
│       │   │   │   └── remote/           # API/service implementations
│       │   │   ├── domain/               # optional use cases and domain rules
│       │   │   └── di/                   # dependency injection bindings
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       ├── test/                         # local unit tests
│       └── androidTest/                  # device/emulator tests
├── gradle/
│   └── libs.versions.toml                # dependency versions, if using catalog
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── README.md
└── docs/
    ├── PRODUCT_BRIEF.md
    ├── ARCHITECTURE.md
    └── PRIVACY_AND_PERMISSIONS.md
```

This is a **conceptual example**, not a rigid required tree. Package names and file placement depend on the app. For a very small app, `core/`, `domain/`, or `di/` may be unnecessary. Avoid creating empty packages just to make the tree look professional.

### Responsibility boundaries

- **Composable/Screen:** Draws UI and sends user actions. It should not perform HTTP calls or directly query the database.
- **ViewModel:** Holds screen state, responds to events, calls repositories/use cases, and exposes observable UI state.
- **UiState:** A data class describing what the screen needs to render, including loading, content, empty, and error states.
- **Repository:** Provides app data and hides whether it came from local storage, a server, or memory.
- **Data source:** Talks to one specific source, such as Room, DataStore, or an HTTP API.
- **Use case/domain layer:** Encodes a business action or rule when that rule is substantial or reused. Do not create a use case for every trivial getter.
- **Navigation:** Defines routes and screen transitions; it should not become a place for business logic.
- **Dependency injection:** Constructs and supplies dependencies. Manual constructor injection is fine in a small app; Hilt can help as the graph grows.

### Expected data flow

```text
User action
    ↓
Composable / Screen
    ↓ event
ViewModel
    ↓ calls
Use case (only if useful)
    ↓
Repository interface
    ↓
Repository implementation
    ├── Local source (Room / DataStore)
    └── Remote source (HTTP API)
    ↓
Flow / Result / domain model
    ↑
ViewModel → UiState → Compose recomposes the screen
```

Do not let the screen skip the ViewModel/repository boundary to make a quick feature work. Shortcuts tend to spread.

---

## 4. What to build first: the order of work

### Phase 1 — Plan
- [ ] Write the one-sentence problem statement.
- [ ] List the primary user and core flow.
- [ ] Draw a rough screen flow on paper.
- [ ] Define the MVP and explicitly list features that are *not* in version 1.
- [ ] Decide whether the app needs an account, backend, offline support, notifications, payments, media, or sensitive permissions.
- [ ] Write basic acceptance criteria for the core flow.

### Phase 2 — Create a clean project
- [ ] Install a stable Android Studio version and use the JDK required by the chosen Android Gradle Plugin.
- [ ] Create a Kotlin Android project. For a new declarative UI, Jetpack Compose is a strong default; Views are still valid when needed.
- [ ] Set a permanent application ID/package name deliberately.
- [ ] Commit the untouched project to Git before major edits.
- [ ] Keep secrets and private signing files out of Git.
- [ ] Build and run the empty app on an emulator and, if available, one real device.
- [ ] Confirm the project builds from the command line as well as from Android Studio.

### Phase 3 — Build one vertical slice
Implement **one complete user journey** before building all screens. For example, in a reading app: open the library → open a book → read a chapter → resume at the saved position.

For that slice:
- [ ] Build the simplest usable UI.
- [ ] Define the screen's `UiState`.
- [ ] Add a ViewModel.
- [ ] Start with fake/in-memory data if the backend is not ready.
- [ ] Add a repository boundary before the screen becomes coupled to the data source.
- [ ] Handle loading, success, empty, and failure states.
- [ ] Add at least one unit test for the important behavior.
- [ ] Check rotation/process recreation where relevant.
- [ ] Test the core journey on a device.

### Phase 4 — Add persistence and network
- [ ] Choose Room for structured local data and DataStore for small preferences/settings.
- [ ] Define what is cached and which source is authoritative.
- [ ] Keep network code behind a remote data source/repository.
- [ ] Model HTTP errors, timeouts, no connection, empty responses, and expired sessions.
- [ ] Use coroutines rather than blocking the main thread.
- [ ] Add retry only where safe; do not blindly retry payments or other non-idempotent operations.
- [ ] If offline behavior matters, make the UI read local data and synchronize deliberately.
- [ ] Never put API secrets that must remain private inside the APK; mobile binaries can be inspected.

### Phase 5 — Make it dependable
- [ ] Add tests around business rules, ViewModels, repositories, and critical UI flows.
- [ ] Add lint/format checks and a CI build if the project is shared or likely to grow.
- [ ] Test on slow network, no network, empty data, large text, dark theme, and smaller/larger screens.
- [ ] Add crash reporting and analytics only with a clear purpose, privacy review, and proper disclosure.
- [ ] Check accessibility: labels, touch target size, contrast, font scaling, and TalkBack.
- [ ] Check battery use, startup time, memory use, and background work.
- [ ] Prepare release signing securely and test a release build, not only debug.

---

## 5. Technology choices: start lean

| Need | Sensible default | Rule |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Use Views if your requirements or existing code call for them. |
| Screen state | ViewModel + `StateFlow` | Keep screen state explicit and observable. |
| Async work | Kotlin coroutines + Flow | Do not block the main thread. |
| Local structured data | Room | Use migrations and test them. |
| Small preferences | DataStore | Avoid storing complex relational data as preferences. |
| Navigation | Navigation for Compose | Keep route definitions and navigation decisions organized. |
| HTTP | Retrofit/OkHttp or another maintained client | Add timeouts, error mapping, and secure transport. |
| Dependency injection | Constructor injection; Hilt as needed | Do not add DI solely for fashion. |
| Background work | WorkManager for deferrable persistent tasks | Do not use it for work that must happen immediately in the foreground. |
| Testing | JUnit plus AndroidX test tools; Compose UI tests where useful | Test behavior and critical user journeys, not just line coverage. |
| Build configuration | Gradle Kotlin DSL; version catalog if useful | Centralize versions and keep build logic understandable. |

Library choice is not a substitute for clear boundaries. Use maintained, compatible versions and check official documentation before adding dependencies.

---

## 6. Kotlin and Compose rules

1. **One source of truth:** Avoid maintaining duplicate copies of the same state in the screen, ViewModel, and repository.
2. **Immutable UI state:** Prefer a data class such as `HomeUiState` with `val` properties.
3. **State in, events out:** A screen receives state and callbacks; it should not own networking or persistence.
4. **Keep composables focused:** Split reusable UI from screen-level orchestration. Do not split every two lines into a new composable.
5. **Lifecycle-aware collection:** Collect flows using lifecycle-aware APIs such as `collectAsStateWithLifecycle()` where appropriate.
6. **Stable keys in lists:** Give lazy list items stable keys when there is a reliable identifier.
7. **No heavy work in composition:** Composables can re-run; do not trigger requests or mutations just because the function was recomposed.
8. **Do not swallow exceptions:** Convert expected failures into UI state, log/report unexpected failures safely, and never show raw stack traces to users.
9. **Save the right state:** Use saved-state mechanisms for small UI state; use a database for durable app data.
10. **Avoid giant ViewModels:** If a ViewModel becomes hard to understand or test, separate responsibilities rather than moving everything into helper classes blindly.

Example shape (illustrative, not a full application):

```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val repository: HomeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadTitle() }
                .onSuccess { title ->
                    _uiState.update {
                        it.copy(isLoading = false, title = title)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't load content.")
                    }
                }
        }
    }
}
```

In a real project, make sure cancellation is handled correctly and do not convert every `Throwable`—including coroutine cancellation—into a normal UI error. The example shows the general shape only.

---

## 7. Precautions that prevent expensive mistakes

### Security and privacy
- Request only the permissions the app actually needs, and request them at the moment the user understands why they are needed.
- Use HTTPS. Do not disable certificate validation to “fix” networking.
- Do not commit API keys, passwords, keystores, access tokens, or production configuration.
- Treat all incoming data as untrusted; validate server responses and user input.
- Keep authentication tokens in platform-appropriate secure storage; avoid logging tokens or personal data.
- Collect the minimum personal data necessary. Document retention, deletion, and account deletion behavior where applicable.
- Review third-party SDKs, their permissions, their data collection, and privacy disclosures.
- Do not assume that obfuscation makes embedded secrets safe.

### Reliability
- Always plan for no network, slow network, server errors, expired login, empty results, and corrupted/old local data.
- Use database migrations and test upgrades from older installed versions.
- Keep database/network work off the main thread.
- Avoid unbounded retries, endless polling, and unnecessary background services.
- Make actions idempotent where possible to avoid duplicates when a request is repeated.
- Do not assume an Activity, process, or ViewModel will live forever.
- Check back navigation, deep links, app resume, and process death for important flows.

### Build and maintenance
- Keep dependencies limited; every library adds update, compatibility, and security work.
- Do not start with dozens of Gradle modules. Split when ownership, reuse, build speed, or dependency boundaries justify it.
- Avoid putting all code in `MainActivity`, one `Utils` file, or one giant ViewModel.
- Use clear names and short functions, but avoid unnecessary abstraction layers.
- Pin and update dependencies deliberately; review release notes and security advisories.
- Keep debug logging out of production builds and make release builds reproducible.
- Maintain a README with setup, build, test, and architecture instructions.

### Product and UX
- Design loading, empty, error, permission-denied, and offline states as real screens—not afterthoughts.
- Support font scaling, TalkBack, adequate contrast, and usable touch targets.
- Do not ask for notification permission on first launch unless it is immediately relevant.
- Explain destructive actions and provide confirmation/undo when appropriate.
- Avoid analytics that do not answer a concrete product question.

---

## 8. When should you split into multiple Gradle modules?

**Start with one module unless you have a reason not to.** Consider feature/core modules when:
- Several developers need clean ownership boundaries.
- Features need to be isolated or reused.
- Build time is becoming a real problem.
- You need strict dependency boundaries.
- A module has a clear independent responsibility and API.

A future larger structure may look like:

```text
:app
:core:model
:core:database
:core:network
:core:data
:core:designsystem
:core:testing
:feature:library
:feature:reader
:feature:settings
```

Do not split modules merely because a reference app does. Every module adds build configuration, dependency management, and navigation/DI complexity. The Now in Android project itself says modularization is a balance and that there is no single correct layout for every project.

---

## 9. Minimum testing plan

| Level | What to test |
|---|---|
| Unit tests | Business rules, formatting, validation, use cases, ViewModel state transitions. |
| Repository/data tests | Mapping, cache behavior, local persistence, error handling, sync rules. |
| UI tests | Important Compose interactions, loading/error/empty states, navigation. |
| Instrumented tests | Features that depend on Android framework/device behavior. |
| Manual device checks | Real network changes, keyboard, permissions, dark theme, font scaling, back button. |
| Release checks | Release build, signing, startup, crash logs, privacy disclosures, upgrade/migration. |

Test the riskiest and most valuable behavior first. A high coverage percentage alone does not prove the app works.

---

## 10. A practical first-week plan

**Day 1:** Define the problem, MVP, core flow, data, and permissions. Sketch screens.

**Day 2:** Create the project, initialize Git, run it on an emulator, and write the README/build steps.

**Day 3:** Implement one screen and its `UiState`/ViewModel using fake data.

**Day 4:** Add the repository and local persistence if the core flow requires it.

**Day 5:** Add loading, empty, error, offline, and retry behavior.

**Day 6:** Write unit tests and one critical UI/integration test. Test on a real device if possible.

**Day 7:** Review privacy/security, remove unused dependencies, run lint/tests, and build a release variant.

The schedule is a guide, not a deadline. A complex app may need several weeks to complete the same steps well.

---

## 11. Before every feature: the feature checklist

- [ ] What user problem does it solve?
- [ ] What is the success state?
- [ ] What are loading, empty, failure, and offline states?
- [ ] What data is read/written, and which layer owns it?
- [ ] Does it need a permission? Can it work without it?
- [ ] What happens if the user leaves the screen or the process is killed?
- [ ] Can the behavior be tested without a real backend?
- [ ] Are errors safe and understandable?
- [ ] Does it work with accessibility and font scaling?
- [ ] Does it introduce a new dependency? Is it justified?

---

## 12. Ready-to-start checklist

Do not consider the project foundation complete until:

- [ ] The app builds from a clean checkout.
- [ ] The app launches on an emulator.
- [ ] The package/application ID is correct.
- [ ] Git ignores secrets and generated build output.
- [ ] The MVP and core flow are documented.
- [ ] One vertical slice works end to end.
- [ ] UI and data responsibilities are separated.
- [ ] The main failure states are handled.
- [ ] At least the important logic has tests.
- [ ] A release build can be produced.
- [ ] Permissions, privacy, and data retention have been reviewed.

## 13. Primary references

1. **Android architecture recommendations** — https://developer.android.com/topic/architecture/recommendations  
   Official guidance for UI/data layers, repositories, Flow/coroutines, and when to use a domain layer.

2. **Now in Android** — https://github.com/android/nowinandroid  
   Large, actively developed Kotlin + Compose reference app. Read the architecture and modularization learning journeys before copying its code structure.

3. **Now in Android: Architecture Learning Journey** — https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md  
   Explains the layers, UDF, and how data moves through the app.

4. **Now in Android: Modularization Learning Journey** — https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md  
   Explains its module strategy and trade-offs.

5. **Android Architecture Samples** — https://github.com/android/architecture-samples  
   A smaller learning project comparing architectural approaches; useful for studying ViewModels, repositories, data sources, and tests.

6. **Android app architecture guide** — https://developer.android.com/topic/architecture  
   Broader official architecture overview.

7. **Android security best practices** — https://developer.android.com/privacy-and-security/security-best-practices  
   Official security guidance to consult before handling accounts, personal data, or sensitive capabilities.

---

## Final rule

**Build the smallest complete user journey first. Keep UI, state, and data responsibilities separate. Test failures as carefully as success. Add architecture only when it solves a real problem.**

That approach is more reliable than copying a large GitHub project's entire structure before you know what your own app needs.
