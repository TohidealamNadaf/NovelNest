# Build setup and diagnostics

Use the checked-in Gradle wrapper with a local JDK 21 and Android SDK 35 (Build Tools 35.0.0).
The project uses Gradle 8.14.3, Android Gradle Plugin (AGP) 8.13.0, Kotlin 2.0.21 and its matching KSP plugin.
Android Studio must support AGP 8.13 (Narwhal 3 / 2025.1.3 or a compatible newer release).
No application SDK target, Java/Kotlin bytecode target, runtime dependency or database version changed in this fix.

`gradle/gradle-daemon-jvm.properties` selects Java 21 for the build daemon. The setting applies to terminal and IDE
builds and avoids a machine-specific Java installation path. Gradle discovers a local matching JDK; this repository
does not configure JDK downloads. If discovery fails, install/register a JDK 21 or select it in the IDE. Pointing
`JAVA_HOME` at JDK 21 also makes the terminal wrapper launcher use that JDK.

## What caused the warnings

The original AGP 8.7.3 exposed nullable Boolean getters named `isCrunchPngs`, `isUseProguard` and
`isWearAppUnbundled`. Gradle 8.14.3 reported these plugin-owned properties as incompatible with future Gradle 9
property rules. A fresh single-use daemon reproduced all three warnings with stack traces in AGP configuration.
A reused daemon could omit them because its generated plugin classes were already cached. Thus an up-to-date
build with no warning output alone was insufficient verification.

AGP 8.13.0 provides the compatible getters and supports this project's existing Gradle wrapper and SDK. Updating
that plugin fixes the owner of the deprecated APIs; warning suppression and a Gradle downgrade are not used.
The app's deprecated `android.kotlinOptions` DSL also moved to typed `kotlin.compilerOptions`, retaining JVM 21.

Daemon logs additionally showed the VS Code Java extension using its bundled Java 25 while terminal builds used
Java 21. These are different daemon environments. The project now requests Java 21 consistently. This does not
change the JVM used to run the editor/language server.

"Busy", "incompatible" and "stopped" daemon counts describe daemon reuse, not a failed APK build. Gradle may still
create another daemon when a compatible one is busy or when JVM arguments/installations differ. Historical daemons
are not killed by the fix. Do not stop all Java processes or delete Gradle caches to address these messages.

## Commands (Windows)

```powershell
.\gradlew.bat --version
.\gradlew.bat --status
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

For a check that cannot hide warnings through daemon class caching:

```powershell
.\gradlew.bat assembleDebug :app:testDebugUnitTest --no-daemon --warning-mode fail
```

Use `--warning-mode all --stacktrace -Dorg.gradle.deprecation.trace=true` when diagnosing a new deprecation.
The ordinary debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

On a new checkout the first build needs network access to resolve official plugin/build-tool artifacts. A plugin JAR
being cached does not guarantee its marker/repository metadata and all build tools are available offline. Subsequent
builds can use `--offline` once the required artifacts have been resolved.

## Scope and interpretation

This addresses build configuration, not UI, narration, network parsers or database behavior. The Gradle daemon JVM
criteria notice is informational while that Gradle feature is incubating. Kotlin compiler warnings about existing
deprecated Android APIs are separate from Gradle's future-version deprecation report and are not hidden by this fix.
The existing `libandroidx.graphics.path.so` stripping notice means that library is packaged without stripping; it
is not the Gradle deprecation warning and is not suppressed here.

## Verification on 24 October 2026

- Reproduced all three AGP 8.7.3 property deprecations in a fresh daemon, while the APK still built successfully.
- AGP 8.13.0 configured in a fresh daemon with `--warning-mode fail` successfully.
- Started the wrapper from VS Code's bundled Java 25 and verified that the build daemon actually used the local
  Java 21 installation selected by the project criteria.
- `assembleDebug :app:testDebugUnitTest --no-daemon --warning-mode fail` succeeded: 200 tests, 198 passed,
  2 skipped, 0 failures/errors. The two live-source smoke checks were not enabled.
- The resulting APK retains package `com.novelnest.app`, min SDK 26, target/compile SDK 35, version 1.0.0/code 1.
  Compiled Kotlin classes retain JVM class-file major version 65 (Java 21).
- The verification log contains none of the three AGP property deprecations and no Gradle deprecation summary.

This verifies the current Gradle 8.14.3 build; it does not certify an upgrade to Gradle 9. Generated diagnostic logs
are under the ignored `build/diagnostics` directory. An old Problems report can remain on disk after a successful
warning-free build; use the current command output and report timestamp rather than a stale report.

Sources:

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://docs.gradle.org/current/userguide/gradle_daemon.html
- https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_boolean_properties
- https://kotlinlang.org/docs/gradle-compiler-options.html
