# Koin 4.2.1 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `koin-android` and `koin-androidx-compose` from 3.5.6 to 4.2.1 without losing functionality, compilation errors, or deprecation warnings.

**Architecture:** Single version variable controls both artifacts; two categories of source changes — one hard API removal in Compose and one deprecated delegate across 6 fragments. No structural changes to the DI graph.

**Tech Stack:** Koin 4.2.1, Android (Kotlin), Jetpack Compose (TV screens only), Gradle Groovy DSL

---

## Background

Current state: `koinVersion = "3.5.6"` in `gradle/versions.gradle`. The app uses `koin-android` (for `by viewModel`, `by inject`, module DSL) and `koin-androidx-compose` (for `koinViewModel` and `get()` in composables). There are no unit tests — verification is compile-based.

### Koin 3.5.6 → 4.x Breaking Changes (this codebase)

| Change | Type | Affected files |
|---|---|---|
| `org.koin.androidx.compose.get` composable removed | Hard break | `TvErrorView.kt` |
| `sharedViewModel` deprecated → `activityViewModel` | Deprecation warning | 6 Fragment files |

### APIs confirmed unchanged — no migration needed

- `by viewModel<T>()` and `by viewModel<T> { parametersOf(...) }` delegates
- `koinViewModel { parametersOf(...) }` Compose helper
- `viewModel { (param: Type) -> ... }` module DSL destructuring
- `startKoin { androidContext(...) }` application setup
- `GlobalContext.get()` used by `safeInject`
- `ParametersDefinition`, `ParametersHolder`, `parametersOf`
- `single { }`, `named()`, `module { }` core DSL
- `org.koin.android.ext.android.get` / `getKoin` Android extensions

---

## File Map

| File | Change |
|---|---|
| `gradle/versions.gradle` | Line 30: bump `koinVersion` |
| `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt` | Replace removed `get()` with `koinInject()` |
| `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/media/episode/EpisodeFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/profile/info/ProfileInfoFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt` | `sharedViewModel` → `activityViewModel` |
| `src/main/kotlin/me/proxer/app/media/info/MediaInfoFragment.kt` | `sharedViewModel` → `activityViewModel` |

---

## Task 1: Bump Koin version and fix hard compilation break

**Goal:** Update `koinVersion` to 4.2.1 and fix the one removed API (`get()` composable in `TvErrorView.kt`) so the project compiles against Koin 4.x.

**Files:**
- Modify: `gradle/versions.gradle:30`
- Modify: `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt:24,32`

**Acceptance Criteria:**
- [ ] `koinVersion` in `gradle/versions.gradle` reads `"4.2.1"`
- [ ] `TvErrorView.kt` imports `koinInject` instead of `get`
- [ ] `TvErrorView.kt` calls `koinInject()` instead of `get()` at the call site
- [ ] `./gradlew compileDebugKotlin` exits 0

**Verify:** `./gradlew compileDebugKotlin --no-daemon` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Bump the version**

  Edit `gradle/versions.gradle` line 30:

  ```groovy
  // Before:
  koinVersion = "3.5.6"

  // After:
  koinVersion = "4.2.1"
  ```

- [ ] **Step 2: Fix TvErrorView.kt — replace removed `get()` composable**

  `org.koin.androidx.compose.get` was removed in Koin 4.x. Replace the import and call site:

  `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt`:

  ```kotlin
  // Before (line 24):
  import org.koin.androidx.compose.get

  // After:
  import org.koin.androidx.compose.koinInject
  ```

  ```kotlin
  // Before (line 32, inside the @Composable function body):
  val preferenceHelper: PreferenceHelper = get()

  // After:
  val preferenceHelper: PreferenceHelper = koinInject()
  ```

- [ ] **Step 3: Compile to verify**

  ```bash
  ./gradlew compileDebugKotlin --no-daemon
  ```

  Expected: `BUILD SUCCESSFUL`. If you see errors other than the one just fixed, triage them now — check whether they are other removed APIs in `koin-androidx-compose` or `koin-android` and apply the same pattern (look up the Koin 4.x equivalent in the Koin migration guide or source).

- [ ] **Step 4: Commit**

  ```bash
  git add gradle/versions.gradle src/main/kotlin/me/proxer/app/tv/TvErrorView.kt
  git commit -m "build: upgrade Koin to 4.2.1, fix removed get() composable"
  ```

---

## Task 2: Replace deprecated `sharedViewModel` with `activityViewModel`

**Goal:** Remove all deprecation warnings by replacing the deprecated `sharedViewModel` delegate (Koin 4.x) with `activityViewModel` in 6 fragment files. Behaviour is identical — both scope the ViewModel to the parent Activity.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt:49,66`
- Modify: `src/main/kotlin/me/proxer/app/media/episode/EpisodeFragment.kt:42,64`
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt:52,70`
- Modify: `src/main/kotlin/me/proxer/app/profile/info/ProfileInfoFragment.kt:25,49`
- Modify: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt:19,33`
- Modify: `src/main/kotlin/me/proxer/app/media/info/MediaInfoFragment.kt:46,66`

**Acceptance Criteria:**
- [ ] No file imports `org.koin.androidx.viewmodel.ext.android.sharedViewModel`
- [ ] No file uses `by sharedViewModel<…>`
- [ ] `./gradlew compileDebugKotlin` exits 0

**Verify:** `./gradlew compileDebugKotlin --no-daemon` → `BUILD SUCCESSFUL`; `grep -r "sharedViewModel" src/` → no output

**Steps:**

The same two-line change applies to all 6 files: (1) update the import, (2) rename the delegate. Apply them in order.

- [ ] **Step 1: EditCommentFragment.kt**

  ```kotlin
  // Line 49 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 66 — delegate:
  // Before:
  override val viewModel by sharedViewModel<EditCommentViewModel> {
  // After:
  override val viewModel by activityViewModel<EditCommentViewModel> {
  ```

- [ ] **Step 2: EpisodeFragment.kt**

  ```kotlin
  // Line 42 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 64 — delegate:
  // Before:
  private val mediaInfoViewModel by sharedViewModel<MediaInfoViewModel>()
  // After:
  private val mediaInfoViewModel by activityViewModel<MediaInfoViewModel>()
  ```

- [ ] **Step 3: MangaFragment.kt**

  ```kotlin
  // Line 52 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 70 — delegate:
  // Before:
  override val viewModel by sharedViewModel<MangaViewModel> { parametersOf(id, language, episode) }
  // After:
  override val viewModel by activityViewModel<MangaViewModel> { parametersOf(id, language, episode) }
  ```

- [ ] **Step 4: ProfileInfoFragment.kt**

  ```kotlin
  // Line 25 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 49 — delegate:
  // Before:
  override val viewModel by sharedViewModel<ProfileViewModel> {
  // After:
  override val viewModel by activityViewModel<ProfileViewModel> {
  ```

- [ ] **Step 5: ProfileSettingsFragment.kt**

  ```kotlin
  // Line 19 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 33 — delegate:
  // Before:
  private val viewModel by sharedViewModel<ProfileSettingsViewModel>()
  // After:
  private val viewModel by activityViewModel<ProfileSettingsViewModel>()
  ```

- [ ] **Step 6: MediaInfoFragment.kt**

  ```kotlin
  // Line 46 — import:
  // Before:
  import org.koin.androidx.viewmodel.ext.android.sharedViewModel
  // After:
  import org.koin.androidx.viewmodel.ext.android.activityViewModel
  ```

  ```kotlin
  // Line 66 — delegate:
  // Before:
  override val viewModel by sharedViewModel<MediaInfoViewModel> { parametersOf(id) }
  // After:
  override val viewModel by activityViewModel<MediaInfoViewModel> { parametersOf(id) }
  ```

- [ ] **Step 7: Verify no sharedViewModel references remain**

  ```bash
  grep -r "sharedViewModel" src/
  ```

  Expected: no output.

- [ ] **Step 8: Compile**

  ```bash
  ./gradlew compileDebugKotlin --no-daemon
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

  ```bash
  git add \
    src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt \
    src/main/kotlin/me/proxer/app/media/episode/EpisodeFragment.kt \
    src/main/kotlin/me/proxer/app/manga/MangaFragment.kt \
    src/main/kotlin/me/proxer/app/profile/info/ProfileInfoFragment.kt \
    src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt \
    src/main/kotlin/me/proxer/app/media/info/MediaInfoFragment.kt
  git commit -m "refactor: replace deprecated sharedViewModel with activityViewModel (Koin 4.x)"
  ```

---

## Task 3: Full build verification and unexpected-error triage

**Goal:** Confirm the full debug APK builds successfully and triage any compilation errors not covered by Tasks 1–2.

**Files:**
- Any files the compiler flags as errors (none expected)

**Acceptance Criteria:**
- [ ] `./gradlew assembleDebug` exits 0
- [ ] No Koin-related compilation errors remain
- [ ] APK exists at `build/outputs/apk/debug/`

**Verify:** `./gradlew assembleDebug --no-daemon --max-workers 2` → `BUILD SUCCESSFUL`; `ls build/outputs/apk/debug/*.apk` → file listed

**Steps:**

- [ ] **Step 1: Run full compile**

  ```bash
  ./gradlew compileDebugKotlin --no-daemon
  ```

  If this fails with errors NOT covered by Tasks 1–2 (i.e., not `get()` / `sharedViewModel`), look up the Koin 4.x equivalent. The most likely surprises are:

  - A composable injecting a singleton still using `get()` somewhere — fix with `koinInject()`
  - Any `org.koin.androidx.viewmodel.ext.android.*` symbol that moved — check `koin-android` 4.x source or changelog

- [ ] **Step 2: Full assembleDebug**

  ```bash
  ./gradlew assembleDebug --no-daemon --max-workers 2
  ```

  Expected: `BUILD SUCCESSFUL`. APK at `build/outputs/apk/debug/`.

- [ ] **Step 3: Verify APK exists**

  ```bash
  ls build/outputs/apk/debug/*.apk
  ```

  Expected: one `.apk` file listed.
