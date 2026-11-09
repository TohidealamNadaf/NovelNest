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

## Gradle 9.7.1 configuration failure (3 November 2026)

Changing only the wrapper to Gradle 9.7.1 breaks AGP 8.13.0 before Android compilation.
AGP 8.x depends on the internal Problems API removed in Gradle 9.6. The error at
app/build.gradle.kts line 4 is plugin application, not an application runtime failure.
The wrapper is restored to the previously verified Gradle 8.14.3 / AGP 8.13.0 pairing.
Keep using the wrapper and the Java 21 daemon criteria; an AGP/Gradle major upgrade
requires a separate compatibility migration.

After pulling the correction, reload the IDE Gradle project. If the IDE still reports
9.7.1, ensure its Gradle import uses the project wrapper rather than a manually selected
Gradle installation. No user Gradle caches or unrelated daemons need to be deleted.

References: [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Gradle maintainers' compatibility explanation](https://github.com/gradle/gradle/issues/38073).

Verification on 3 November 2026: wrapper reports Gradle 8.14.3 and Java 21.0.9.
assembleDebug --offline --max-workers=1 --warning-mode fail succeeded (39 tasks,
21 executed, 18 from cache). Existing Kotlin Android-API deprecation warnings and
the libandroidx.graphics.path.so stripping notice remain; neither is this plugin
configuration error. No automated tests or physical-device animation checks ran
for the footer repair in this build.

## Update rejected after debug signing key changed (3 November 2026)

The Samsung SM-S938B still has the 1 November build of com.novelnest.app installed.
A data-preserving adb install -r -t of the new APK was rejected with:

    INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.novelnest.app
    signatures do not match newer version; ignoring!

Public signer SHA-256 fingerprints verified with apksigner:

- Installed APK / 1 November instrumentation APK:
  c48853047a3c0d3d4c82ada1e3c7df22c1675ad12add9d025456dee7c3d71233
- 3 November app APK / current default debug keystore:
  0125ad02cedfbcf8dc8e0e92eb0fca4e2d9094309589cc1eb378dab451ef4325

The current user-profile debug.keystore was created on 3 November. Gradle's default
debug signing uses that file, so replacing the file changes the application's signing
identity. The new APK passes signature verification, retains the same application ID,
and includes all four configured ABIs. Both builds have versionCode 1; equal version
codes permit a manual replacement when the signing identity matches. Incrementing
the version or changing Gradle versions cannot repair this signature mismatch.

Recovery requires the original private debug signing key (for example, the old
Windows profile's .android/debug.keystore). Keep the current keystore backed up
before restoring another one; other apps may now depend on its identity. Prefer an
explicit local debug signing configuration if retaining multiple development keys.
Do not commit private keystores or their passwords. Preserve the original key in a
secure backup for every build distributed to the same installation population.

A certificate or old APK cannot reconstruct the private signing key. No app uninstall,
data clear, signing-key replacement, or package-ID change was performed during this
diagnosis. If the original key cannot be recovered, a replacement installation is a
separate data-migration decision requiring approval; credential keys in Android
Keystore cannot simply be copied across an uninstall.

Reference: [Android app update signing requirements](https://developer.android.com/google/play/app-updates).
