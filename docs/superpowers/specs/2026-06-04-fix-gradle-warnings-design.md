# Fix Gradle Build Warnings — Design

**Date:** 2026-06-04  
**Branch:** fix-warnings  

## Problem

`./gradlew compileDebugKotlin` emits five categories of warnings:

| # | Source | Warning |
|---|--------|---------|
| 1 | Gradle JVM | `java.lang.System::load` restricted method (native-platform.jar) |
| 2 | Room KSP | `MessengerDao.kt:101` nullable return type on `LiveData` is meaningless |
| 3 | Room KSP | `MessengerDao.kt:101` query returns unused column `conferenceId` |
| 4 | `org.jetbrains.kotlin.android` plugin | AGP obsolete variant API: `applicationVariants`, `testVariants`, `unitTestVariants` |
| 5 | `org.jetbrains.kotlin.android` plugin | `Deprecated 'org.jetbrains.kotlin.android' plugin usage` |

Warnings 4–5 are caused by the external Kotlin plugin calling AGP's legacy variant API. Root cause: `android.builtInKotlin=false` + `android.newDsl=false` are required to use Kotlin 2.4.0 (external plugin) with AGP 9, because AGP 9.2.1 only bundles Kotlin 2.2.10.

## Decision

Migrate to AGP's built-in Kotlin (2.2.10), accepting the version downgrade from 2.4.0. This eliminates all five warning categories. The project does not use any Kotlin 2.3+ or 2.4+ language features that would block this downgrade.

## Phase 1 — Built-in Kotlin Migration

Eliminates warnings 4 and 5.

### `gradle/versions.gradle`
- Set `kotlinVersion = "2.2.10"` (AGP 9.2.1 bundled version)
- Set `kspVersion` to the KSP release compatible with Kotlin 2.2.10 (verify on https://github.com/google/ksp/releases)

### `gradle.properties`
- Remove `android.builtInKotlin=false`
- Remove `android.newDsl=false`
- Remove both keys from `android.suppressUnsupportedOptionWarnings`

### `settings.gradle`
- Remove `id "org.jetbrains.kotlin.android" version "${kotlinVersion}"` from `pluginManagement.plugins`
- Remove `id "org.jetbrains.kotlin.plugin.compose" version "${kotlinVersion}"` from `pluginManagement.plugins`

### `build.gradle`
- Remove `id "org.jetbrains.kotlin.android"` from `plugins` block
- Remove `id "org.jetbrains.kotlin.plugin.compose"` from `plugins` block
- Update or migrate `tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach { kotlinOptions { ... } }` — with built-in Kotlin, AGP 9 prefers `kotlin { compilerOptions { ... } }` DSL; verify which form compiles cleanly
- Compose compiler: AGP manages it automatically via `buildFeatures { compose = true }` when using built-in Kotlin — no external compose plugin needed

### `CLAUDE.md`
- Update the "Key Gotchas" section: remove the `builtInKotlin`/`newDsl` entry and replace with a note that the project uses built-in Kotlin 2.2.10 (AGP bundled). Update the toolchain table Kotlin row to `2.2.10`.

### Verification gate
```
./gradlew compileDebugKotlin --no-daemon --max-workers 2 --warning-mode all
```
Must produce zero `w:` lines related to Kotlin plugin deprecation or `applicationVariants`.

## Phase 2 — Code Warning Fixes

Eliminates warnings 1, 2, and 3.

### Warning 1 — JVM native access (`gradle.properties`)

Add `--enable-native-access=ALL-UNNAMED` to `org.gradle.jvmargs`:

```properties
org.gradle.jvmargs=-Xmx2048M -XX:MaxHeapSize=1024M -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
```

### Warning 2 — Room DAO nullable return type (`MessengerDao.kt:101`)

Remove `?` from the return type:

```kotlin
// before
abstract fun getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>?>

// after
abstract fun getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>>
```

### Warning 3 — Room query unused column (`MessengerDao.kt:101`)

Add `@RewriteQueriesToDropUnusedColumns` above the existing `@Query` annotation on the same function:

```kotlin
@RewriteQueriesToDropUnusedColumns
@Query("SELECT ...")
abstract fun getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>>
```

This instructs Room's KSP processor to rewrite the query and drop the unused `conferenceId` column instead of warning about the mismatch.

### Verification gate
```
./gradlew clean kspDebugKotlin compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks --warning-mode all
```
Must produce zero `w:` lines.

## Out of Scope

- No changes to app logic, UI, or runtime behavior
- No Kotlin API changes — the 2.4.0 → 2.2.10 downgrade is purely a toolchain change; no source files require modification beyond the build scripts and `MessengerDao.kt`
- No suppression annotations or `@Suppress` added to source files (fixes are real, not workarounds)
