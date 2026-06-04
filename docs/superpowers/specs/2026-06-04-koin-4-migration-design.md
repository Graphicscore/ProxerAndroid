# Koin 4.2.1 Migration Design

**Date:** 2026-06-04
**Scope:** `koin-android` + `koin-androidx-compose` upgrade from 3.5.6 → 4.2.1

---

## Goal

Upgrade Koin to 4.2.1 without losing functionality and without compilation errors or deprecation warnings.

---

## Current State

- `gradle/versions.gradle`: `koinVersion = "3.5.6"`
- Dependencies used: `io.insert-koin:koin-android` and `io.insert-koin:koin-androidx-compose`
- No separate `koin-androidx-viewmodel` artifact (already bundled into `koin-android`)

---

## Changes

### 1. Version bump

**File:** `gradle/versions.gradle`

```
koinVersion = "3.5.6"  →  koinVersion = "4.2.1"
```

Single change; both artifacts consume this variable.

### 2. Hard compilation break — `get()` removed in compose

**File:** `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt`

`org.koin.androidx.compose.get` composable was removed in Koin 4.x.

```kotlin
// Remove:
import org.koin.androidx.compose.get
val preferenceHelper: PreferenceHelper = get()

// Replace with:
import org.koin.androidx.compose.koinInject
val preferenceHelper: PreferenceHelper = koinInject()
```

### 3. Deprecation fixes — `sharedViewModel` → `activityViewModel`

`sharedViewModel` was deprecated in Koin 4.x in favour of `activityViewModel` (matches AndroidX naming). Both scope the ViewModel to the parent Activity — behaviour is identical.

Affected files (6 total):

| File | Change |
|---|---|
| `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/media/episode/EpisodeFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/profile/info/ProfileInfoFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/media/info/MediaInfoFragment.kt` | `sharedViewModel` → `activityViewModel` |

Import change in each: `org.koin.androidx.viewmodel.ext.android.sharedViewModel` → `org.koin.androidx.viewmodel.ext.android.activityViewModel`

### 4. Compile verification

After changes:
1. `./gradlew compileDebugKotlin` — fast type-check; fix any unexpected errors
2. `./gradlew assembleDebug` — full build sanity check

---

## APIs Confirmed Unchanged in 4.x

These require no changes:

- `koinViewModel { parametersOf(...) }` — Compose ViewModel injection
- `by viewModel<T> { parametersOf(...) }` — Fragment/Activity ViewModel delegate
- `viewModel { (param: Type) -> ... }` — Module DSL with destructuring
- `startKoin { androidContext(...) }` — Application setup
- `GlobalContext.get()` — Used by `safeInject`
- `ParametersDefinition` / `ParametersHolder` — Parameter types
- `parametersOf(...)` — Parameter factory
- `single { }`, `named()`, `module { }` — Core DSL
- `org.koin.android.ext.android.get` / `getKoin` — Android component extensions

---

## Out of Scope

- Other dependency upgrades
- Architectural changes to how ViewModels are structured
- Adding Koin test modules
