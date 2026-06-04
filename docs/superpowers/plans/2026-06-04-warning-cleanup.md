# Warning Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all Kotlin compiler `w:` warnings and ktlint violations without changing runtime behaviour.

**Architecture:** Ktlint violations are auto-fixed via `ktlintFormat` after excluding vendor packages. Kotlin-level deprecations are resolved by changing imports or call sites. Android API deprecations use `androidx` compat wrappers (`IntentCompat`, `BundleCompat`, `WindowInsetsControllerCompat`, `MenuProvider`) which work on minSdk 23 without version guards. The handful of deprecated APIs with no compat replacement receive targeted `@Suppress("DEPRECATION")`.

**Tech Stack:** Kotlin, AGP 9.2.1, `androidx.core:core-ktx`, `androidx.activity:activity-ktx`, `androidx.core:core-bundle` (new dep), ktlint via `org.jlleitschuh.gradle.ktlint`

---

### Task 1: Ktlint — vendor exclusions + auto-format

**Goal:** Make `ktlintCheck` pass by excluding vendor-bundled files and auto-formatting all proxer-owned source files.

**Files:**
- Modify: `.editorconfig`
- Run tool: `./gradlew ktlintFormat`

**Acceptance Criteria:**
- [ ] Five vendor files excluded from ktlint: `me/zhanghai/**`, `com/gojuno/**`, `androidx/recyclerview/widget/BindAwareViewHolder.kt`
- [ ] `./gradlew ktlintCheck --no-daemon --max-workers 2` exits 0

**Verify:** `./gradlew ktlintCheck --no-daemon --max-workers 2` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add vendor exclusions to `.editorconfig`**

Append to the end of `.editorconfig`:

```ini
[src/main/kotlin/me/zhanghai/**]
ktlint_disabled = true

[src/main/kotlin/com/gojuno/**]
ktlint_disabled = true

[src/main/kotlin/androidx/recyclerview/widget/BindAwareViewHolder.kt]
ktlint_disabled = true
```

- [ ] **Step 2: Run auto-formatter**

```bash
./gradlew ktlintFormat --no-daemon --max-workers 2
```

Expected: prints which files were reformatted, exits 0.

- [ ] **Step 3: Verify**

```bash
./gradlew ktlintCheck --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add .editorconfig
git add -u src/
git commit -m "style: fix ktlint violations, exclude vendor files"
```

---

### Task 2: Kotlin stdlib + Koin DSL + bundleOf imports

**Goal:** Eliminate compiler warnings from deprecated Kotlin stdlib (`sumBy`), deprecated Koin ViewModel DSL import, and deprecated `androidx.core.os.bundleOf`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/sync/MessengerNotifications.kt`
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt`
- Modify: `gradle/dependencies.gradle`
- Modify: `gradle/versions.gradle`
- Mass-replace import: all 48 files using `import androidx.core.os.bundleOf`

**Acceptance Criteria:**
- [ ] No `sumBy` calls remain in the codebase
- [ ] `MainModules.kt` imports `org.koin.core.module.dsl.viewModel` (not `org.koin.androidx.viewmodel.dsl.viewModel`)
- [ ] `androidx.core:core-bundle` added to dependencies
- [ ] Zero `import androidx.core.os.bundleOf` remain; all use `import androidx.core.bundle.bundleOf`
- [ ] `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -E "sumBy|viewModel.*Moved|bundleOf"` → empty

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -E "sumBy|viewModel.*Moved|bundleOf"` → no output

**Steps:**

- [ ] **Step 1: Fix `sumBy` in `MessengerNotifications.kt`**

Find line (approx line 79):
```kotlin
val messageAmount = filteredConferenceMap.values.sumBy { it.size }
```
Replace with:
```kotlin
val messageAmount = filteredConferenceMap.values.sumOf { it.size }
```

- [ ] **Step 2: Fix Koin ViewModel DSL import in `MainModules.kt`**

Change line 84:
```kotlin
import org.koin.androidx.viewmodel.dsl.viewModel
```
to:
```kotlin
import org.koin.core.module.dsl.viewModel
```

This single import change eliminates ~50 compiler warnings.

- [ ] **Step 3: Add `core-bundle` version to `gradle/versions.gradle`**

Add inside the `ext` block (near the other `androidx` versions):
```groovy
coreBundleVersion = "1.0.0"
```

- [ ] **Step 4: Add `core-bundle` dependency to `gradle/dependencies.gradle`**

Add alongside the existing `core-ktx` line:
```groovy
implementation "androidx.core:core-bundle:$coreBundleVersion"
```

- [ ] **Step 5: Mass-replace `bundleOf` import in all 48 files**

```bash
find src/main/kotlin src/debug/kotlin -name "*.kt" -exec \
  sed -i 's|import androidx.core.os.bundleOf|import androidx.core.bundle.bundleOf|g' {} +
```

Verify the replacement:
```bash
grep -r "import androidx.core.os.bundleOf" src/ --include="*.kt" | wc -l
```
Expected: `0`

- [ ] **Step 6: Commit**

```bash
git add gradle/versions.gradle gradle/dependencies.gradle
git add -u src/
git commit -m "fix: replace deprecated Koin DSL import, sumBy, bundleOf"
```

---

### Task 3: Parcelable and Serializable compat

**Goal:** Replace all deprecated `getParcelable*` and `getSerializable*` calls with `IntentCompat`/`BundleCompat` equivalents. Fix the two central extension wrappers first, then fix the remaining direct call sites.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/extension/NullabilityExtensions.kt`
- Modify: `src/main/kotlin/me/proxer/app/MainActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/AnimeActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/MediaActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/comments/CommentsFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/list/MediaListFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/PrvMessengerActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceParticipantAdapter.kt`
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/bookmark/BookmarkFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/stream/TvStreamActivity.kt`

**Acceptance Criteria:**
- [ ] `NullabilityExtensions.kt` uses `IntentCompat.getParcelableExtra` and `BundleCompat.getParcelable` (both have `reified T`)
- [ ] No raw `intent.getParcelableExtra<T>(key)`, `intent.getSerializableExtra(key)`, `bundle.getParcelable<T>(key)`, `bundle.getParcelableArrayList<T>(key)`, or `bundle.getSerializable(key)` calls remain outside of `NullabilityExtensions.kt`
- [ ] `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "parcelable|serializable"` → empty

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "parcelable|serializable"` → no output

**Steps:**

- [ ] **Step 1: Fix `NullabilityExtensions.kt` — update both wrappers and add missing ones**

Full new content for the relevant section (imports to add: `IntentCompat`, `BundleCompat`):

```kotlin
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
```

Replace the two existing deprecated wrappers and add new serializable wrappers:

```kotlin
inline fun <reified T : Parcelable> Intent.getSafeParcelableExtra(key: String) =
    requireNotNull(IntentCompat.getParcelableExtra(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Bundle.getSafeParcelable(key: String) =
    requireNotNull(BundleCompat.getParcelable(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Serializable> Intent.getSafeSerializableExtra(key: String) =
    requireNotNull(IntentCompat.getSerializableExtra(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Serializable> Bundle.getSafeSerializable(key: String) =
    requireNotNull(BundleCompat.getSerializable(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Intent.getSafeParcelableArrayListExtra(key: String) =
    requireNotNull(IntentCompat.getParcelableArrayListExtra(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Bundle.getSafeParcelableArrayList(key: String) =
    requireNotNull(BundleCompat.getParcelableArrayList(this, key, T::class.java)) { "No value found for key $key" }
```

Add `java.io.Serializable` to imports if not present.

Note: `IntentCompat.getSerializableExtra` may not exist in all versions of `core`. If it doesn't compile, use `BundleCompat.getSerializable` for bundle variants and add `@Suppress("DEPRECATION")` only on `Intent.getSafeSerializableExtra` using `intent.getSerializableExtra(key) as? T`.

- [ ] **Step 2: Fix raw call sites — activities**

**`MainActivity.kt` line ~153:** Replace:
```kotlin
data?.getParcelableArrayListExtra<Option>(OPTION_RESULT)?.forEach { option ->
```
with:
```kotlin
IntentCompat.getParcelableArrayListExtra(data ?: return, OPTION_RESULT, Option::class.java)?.forEach { option ->
```
Add import `androidx.core.content.IntentCompat`.

**`MainActivity.kt` line ~247:** Replace:
```kotlin
val sectionExtra = intent.getSerializableExtra(SECTION_EXTRA) as? DrawerItem
```
with:
```kotlin
val sectionExtra = IntentCompat.getSerializableExtra(intent, SECTION_EXTRA, DrawerItem::class.java)
```
(or use the new extension: `intent.getSafeSerializableExtra<DrawerItem>(SECTION_EXTRA)` wrapped in a try if nullable is needed)

Actually, since the cast was `as?` (nullable), the pattern is:
```kotlin
val sectionExtra = BundleCompat.getSerializable(intent.extras ?: Bundle(), SECTION_EXTRA, DrawerItem::class.java)
```
Or simpler — keep the `as?` cast if `IntentCompat.getSerializableExtra` isn't available and suppress:
```kotlin
@Suppress("DEPRECATION")
val sectionExtra = intent.getSerializableExtra(SECTION_EXTRA) as? DrawerItem
```
Prefer the compat version if `IntentCompat.getSerializableExtra` compiles.

**`AnimeActivity.kt` line ~73:** Replace:
```kotlin
true -> intent.getSerializableExtra(LANGUAGE_EXTRA) as AnimeLanguage
```
with:
```kotlin
true -> IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java)!!
```
Add import `androidx.core.content.IntentCompat`.

**`MangaActivity.kt` line ~101:** Replace:
```kotlin
true -> intent.getSerializableExtra(LANGUAGE_EXTRA) as Language
```
with:
```kotlin
true -> IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, Language::class.java)!!
```

**`StreamActivity.kt` line ~116:** Replace:
```kotlin
get() = intent.getSerializableExtra(LANGUAGE_EXTRA) as AnimeLanguage
```
with:
```kotlin
get() = IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java)!!
```

**`StreamActivity.kt` lines ~119, ~141:** Replace:
```kotlin
get() = intent.getParcelableExtra(COVER_EXTRA)
get() = intent.getParcelableExtra(AD_TAG_EXTRA)
```
with (using the extension wrapper, specifying type):
```kotlin
get() = IntentCompat.getParcelableExtra(intent, COVER_EXTRA, Uri::class.java)
get() = IntentCompat.getParcelableExtra(intent, AD_TAG_EXTRA, Uri::class.java)
```
Check actual types from the companion `putExtra` calls in the same file to use the correct class.

**`MediaActivity.kt` line ~95:** Replace:
```kotlin
get() = intent.getSerializableExtra(CATEGORY_EXTRA) as Category?
```
with:
```kotlin
get() = IntentCompat.getSerializableExtra(intent, CATEGORY_EXTRA, Category::class.java)
```

**`PrvMessengerActivity.kt` line ~59:** Replace:
```kotlin
val conference = intent.getParcelableExtra<LocalConference>(CONFERENCE_EXTRA)
```
with:
```kotlin
val conference = IntentCompat.getParcelableExtra(intent, CONFERENCE_EXTRA, LocalConference::class.java)
```

**`CreateConferenceActivity.kt` line ~41:** Replace:
```kotlin
get() = intent.getParcelableExtra(INITIAL_PARTICIPANT_EXTRA)
```
Determine type from companion `putExtra` call, then:
```kotlin
get() = IntentCompat.getParcelableExtra(intent, INITIAL_PARTICIPANT_EXTRA, LocalUser::class.java)
```
(Check the actual type name — it may be a different class)

**`EditCommentActivity.kt` line ~138:** Replace:
```kotlin
return intent?.getParcelableExtra(COMMENT_EXTRA)
```
with:
```kotlin
return intent?.let { IntentCompat.getParcelableExtra(it, COMMENT_EXTRA, LocalComment::class.java) }
```
(Substitute actual type)

- [ ] **Step 3: Fix raw call sites — fragments**

**`MangaFragment.kt` line ~146:** Replace:
```kotlin
lastPosition = savedInstanceState?.getParcelable(LAST_POSITION_STATE)
```
with:
```kotlin
lastPosition = savedInstanceState?.let { BundleCompat.getParcelable(it, LAST_POSITION_STATE, ScrollPosition::class.java) }
```
(Substitute actual `ScrollPosition` or whatever type is stored)

**`CreateConferenceParticipantAdapter.kt` line ~44:** Replace:
```kotlin
data = savedInstanceState?.getParcelableArrayList(LIST_STATE) ?: emptyList()
```
with:
```kotlin
data = savedInstanceState?.let { BundleCompat.getParcelableArrayList(it, LIST_STATE, LocalUser::class.java) } ?: emptyList()
```
(Substitute actual item type)

**`BookmarkFragment.kt` line ~80:** Replace:
```kotlin
get() = requireArguments().getSerializable(CATEGORY_ARGUMENT) as? Category
```
with:
```kotlin
get() = BundleCompat.getSerializable(requireArguments(), CATEGORY_ARGUMENT, Category::class.java)
```

**`CommentsFragment.kt` line ~77:** Replace:
```kotlin
get() = requireArguments().getSerializable(SORT_CRITERIA_ARGUMENT) as? CommentSortCriteria
```
with:
```kotlin
get() = BundleCompat.getSerializable(requireArguments(), SORT_CRITERIA_ARGUMENT, CommentSortCriteria::class.java)
```

**`MediaListFragment.kt` lines ~94–183:** Replace all `requireArguments().getSerializable(KEY) as? Type` calls:
```kotlin
// Old:
get() = requireArguments().getSerializable(CATEGORY_ARGUMENT) as Category
// New:
get() = BundleCompat.getSerializable(requireArguments(), CATEGORY_ARGUMENT, Category::class.java)!!

// Old:
get() = requireArguments().getSerializable(SORT_CRITERIA_ARGUMENT) as? MediaSearchSortCriteria
// New:
get() = BundleCompat.getSerializable(requireArguments(), SORT_CRITERIA_ARGUMENT, MediaSearchSortCriteria::class.java)
```

Replace `getParcelableArrayList` calls:
```kotlin
// Old:
get() = requireArguments().getParcelableArrayList(GENRES_ARGUMENT) ?: emptyList()
// New:
get() = BundleCompat.getParcelableArrayList(requireArguments(), GENRES_ARGUMENT, Genre::class.java) ?: emptyList()
```
Apply same pattern for `EXCLUDED_GENRES_ARGUMENT`, `TAGS_ARGUMENT`, `EXCLUDED_TAGS_ARGUMENT`.

**`ProfileCommentFragment.kt` line ~64:** Replace:
```kotlin
get() = requireArguments().getSerializable(CATEGORY_ARGUMENT) as? Category
```
with:
```kotlin
get() = BundleCompat.getSerializable(requireArguments(), CATEGORY_ARGUMENT, Category::class.java)
```

**`ProfileMediaListFragment.kt` lines ~68–71:** Replace both `getSerializable` calls with `BundleCompat.getSerializable`.

**`TvStreamActivity.kt` line ~18:** Replace:
```kotlin
(intent.getSerializableExtra(LANGUAGE_EXTRA) as? AnimeLanguage) ?: AnimeLanguage.ENGLISH_SUB
```
with:
```kotlin
IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java) ?: AnimeLanguage.ENGLISH_SUB
```

Add `import androidx.core.content.IntentCompat` and `import androidx.core.os.BundleCompat` wherever needed.

- [ ] **Step 4: Compile-check**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Fix any type errors (wrong class passed to compat call). The compiler will point you to each mismatch.

- [ ] **Step 5: Commit**

```bash
git add -u src/
git commit -m "fix: replace deprecated Parcelable/Serializable APIs with IntentCompat/BundleCompat"
```

---

### Task 4: Fragment options menu — MenuProvider migration

**Goal:** Replace deprecated `setHasOptionsMenu` + `onCreateOptionsMenu` + `onOptionsItemSelected` in all affected fragments with the `MenuProvider` API.

**Files (24 fragments):**
- `src/main/kotlin/me/proxer/app/bookmark/BookmarkFragment.kt`
- `src/main/kotlin/me/proxer/app/chat/ChatContainerFragment.kt`
- `src/main/kotlin/me/proxer/app/chat/prv/conference/ConferenceFragment.kt`
- `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerFragment.kt`
- `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt`
- `src/main/kotlin/me/proxer/app/forum/TopicFragment.kt`
- `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt`
- `src/main/kotlin/me/proxer/app/media/comments/CommentsFragment.kt`
- `src/main/kotlin/me/proxer/app/media/list/MediaListFragment.kt`
- `src/main/kotlin/me/proxer/app/notification/NotificationFragment.kt`
- `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentFragment.kt`
- `src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListFragment.kt`
- And any others the compiler flags (run `./gradlew compileDebugKotlin 2>&1 | grep "onCreateOptionsMenu\|setHasOptionsMenu" | grep "^w:"` to get full list)

Also for `Activity` subclasses using the deprecated `onCreateOptionsMenu`:
- `src/main/kotlin/me/proxer/app/anime/AnimeActivity.kt`
- `src/main/kotlin/me/proxer/app/base/ImageTabsActivity.kt`
- `src/main/kotlin/me/proxer/app/base/BaseActivity.kt`
- `src/main/kotlin/me/proxer/app/media/MediaActivity.kt`
- `src/main/kotlin/me/proxer/app/base/DrawerActivity.kt`
- `src/main/kotlin/me/proxer/app/ui/WebViewActivity.kt`
- `src/main/kotlin/me/proxer/app/forum/TopicActivity.kt`
- `src/main/kotlin/me/proxer/app/info/industry/IndustryActivity.kt`
- `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupActivity.kt`
- `src/main/kotlin/me/proxer/app/manga/MangaActivity.kt`
- `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt`

**Acceptance Criteria:**
- [ ] No `setHasOptionsMenu()` calls remain in any fragment
- [ ] No `override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater)` overrides remain in fragments
- [ ] No `override fun onOptionsItemSelected(item: MenuItem): Boolean` overrides remain in fragments
- [ ] `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "onCreateOptionsMenu|onOptionsItemSelected|setHasOptionsMenu"` → empty

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "onCreateOptionsMenu|onOptionsItemSelected|setHasOptionsMenu"` → no output

**Steps:**

- [ ] **Step 1: Understand the migration pattern**

**For each Fragment** — transform:
```kotlin
// In onCreate:
setHasOptionsMenu(true)

// Deprecated overrides:
override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.fragment_foo, menu)
    super.onCreateOptionsMenu(menu, inflater)
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_bar -> { doSomething(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
```

Into:
```kotlin
// In onViewCreated, add:
requireActivity().addMenuProvider(object : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_foo, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_bar -> { doSomething(); true }
            else -> false
        }
    }
}, viewLifecycleOwner)
```

Remove the `setHasOptionsMenu(true)` call and the three deprecated override functions.

Add imports:
```kotlin
import androidx.core.view.MenuProvider
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
```

Note: `super.onOptionsItemSelected(item)` becomes `false` in `onMenuItemSelected` — returning `false` signals "not handled", which is what `super` would do.

**For Activity subclasses** — `onCreateOptionsMenu(Menu)` and `onOptionsItemSelected(MenuItem)` on Activities are NOT deprecated; only the Fragment variants are. Check compiler output carefully before modifying activities.

- [ ] **Step 2: Apply migration to each fragment**

Work through each fragment in the list. For each:
1. Remove `setHasOptionsMenu(true)` from `onCreate`/`onViewCreated`
2. Remove deprecated `onCreateOptionsMenu` and `onOptionsItemSelected` overrides
3. Add `addMenuProvider(...)` call in `onViewCreated` (before `super.onViewCreated` is fine, or after — lifecycle binding ensures correct ordering)

- [ ] **Step 3: Compile and fix**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Address any remaining usages the compiler flags.

- [ ] **Step 4: Commit**

```bash
git add -u src/
git commit -m "fix: migrate fragment menus to MenuProvider API"
```

---

### Task 5: Window and system bar APIs — WindowInsetsControllerCompat

**Goal:** Replace all `systemUiVisibility`, `FLAG_TRANSLUCENT_*`, `statusBarColor`, and `setOnSystemUiVisibilityChangeListener` usage with `WindowInsetsControllerCompat`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/base/DrawerActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/settings/theme/Theme.kt` (if it uses `statusBarColor`)
- Modify: `src/main/kotlin/me/proxer/app/settings/theme/ThemeVariant.kt` (if it uses `statusBarColor`)

**Acceptance Criteria:**
- [ ] No `FLAG_TRANSLUCENT_STATUS` or `FLAG_TRANSLUCENT_NAVIGATION` usages
- [ ] No `View.systemUiVisibility = ...` assignments
- [ ] No `SYSTEM_UI_FLAG_*` constants referenced
- [ ] No `setOnSystemUiVisibilityChangeListener` calls
- [ ] No `window.statusBarColor` assignments (unless via theme attribute)
- [ ] `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "systemUi|translucent|statusBar"` → empty

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "systemUi|translucent|statusBar"` → no output

**Steps:**

- [ ] **Step 1: Fix `DrawerActivity.kt` — statusBarColor**

Find line ~53:
```kotlin
window.statusBarColor = Color.TRANSPARENT
```

Replace with (using theme, since this is just making status bar transparent to let content show through):
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```
Add import: `import androidx.core.view.WindowCompat`

Note: if the activity already calls `setDecorFitsSystemWindows` elsewhere, remove the duplicate. The transparent status bar is a side effect of edge-to-edge mode, which `setDecorFitsSystemWindows(window, false)` enables.

If `statusBarColor` is set to a specific non-transparent colour for theming, instead use the theme attribute approach (set `android:statusBarColor` in the activity theme in `themes.xml`), or use `WindowInsetsControllerCompat.isAppearanceLightStatusBars`.

- [ ] **Step 2: Fix `StreamActivity.kt` — full immersive mode**

`StreamActivity` uses `systemUiVisibility` extensively. The full migration:

Add imports at top:
```kotlin
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
```

Remove all the old `SYSTEM_UI_FLAG_*` imports (lines 18–24) and the `rxbinding3.view.systemUiVisibilityChanges` import.

**Replace `window.addFlags(FLAG_TRANSLUCENT_STATUS/NAVIGATION)` (~line 460–461):**
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```
Call this once in `onCreate` instead of the two `addFlags` calls.

**Replace `systemUiVisibility` read (~line 479):**
```kotlin
// Old:
val isInFullscreenMode = window.decorView.systemUiVisibility and SYSTEM_UI_FLAG_FULLSCREEN != 0
// New — track state in a field instead of reading from view:
```
Add a `private var isInFullscreenMode = false` field. Update it whenever you set the insets controller state.

**Replace `systemUiVisibility` write (~lines 500–508):**
```kotlin
// Old:
window.decorView.systemUiVisibility = when {
    condition ->
        SYSTEM_UI_FLAG_LOW_PROFILE or
            SYSTEM_UI_FLAG_FULLSCREEN or
            SYSTEM_UI_FLAG_LAYOUT_STABLE or
            SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            SYSTEM_UI_FLAG_IMMERSIVE
    else -> SYSTEM_UI_FLAG_VISIBLE
}

// New:
val controller = WindowInsetsControllerCompat(window, window.decorView)
if (condition) {
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    isInFullscreenMode = true
} else {
    controller.show(WindowInsetsCompat.Type.systemBars())
    isInFullscreenMode = false
}
```

**Replace `systemUiVisibilityChanges()` observable (~line 456):**

`rxbinding3.view.systemUiVisibilityChanges()` emits visibility change events. Replace with `ViewCompat.setOnApplyWindowInsetsListener`:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
    val visible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
    // drive whatever the visibility change did before
    onSystemBarsVisibilityChanged(visible)
    insets
}
```

If the rxbinding observable was used in an RxJava chain, adapt accordingly (e.g., use `PublishSubject<Boolean>` fed from the listener).

- [ ] **Step 3: Fix `Theme.kt` and `ThemeVariant.kt`**

Run:
```bash
grep -n "statusBarColor\|systemUiVisibility\|SYSTEM_UI_FLAG\|FLAG_TRANSLUCENT" \
  src/main/kotlin/me/proxer/app/settings/theme/Theme.kt \
  src/main/kotlin/me/proxer/app/settings/theme/ThemeVariant.kt
```

For any `window.statusBarColor = ...` patterns, replace with theme attribute (`android:statusBarColor`) in `res/values/themes.xml` or use the `WindowInsetsControllerCompat` approach from Step 1.

- [ ] **Step 4: Verify**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | grep -iE "systemUi|translucent|statusBar"
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -u src/
git commit -m "fix: migrate window/system bar APIs to WindowInsetsControllerCompat"
```

---

### Task 6: Miscellaneous compat fixes

**Goal:** Fix the remaining small deprecated-API call sites: `scaledDensity`, `defaultDisplay.getMetrics()`, `ViewCompat.animate()`, `ViewCompat.isAttachedToWindow()`, `ShareCompat.IntentBuilder.from()`, `InputMethodManager.SHOW_IMPLICIT`, `MasterKey.Builder(context)`, and Media3 `C.TYPE_*`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/extension/AndroidExtensions.kt`
- Modify: `src/main/kotlin/me/proxer/app/util/DeviceUtils.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/view/bbcode/BBCodeView.kt`
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/comments/CommentsAdapter.kt`
- Modify: `src/main/kotlin/me/proxer/app/news/NewsAdapter.kt`
- Modify: `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentAdapter.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/view/ExpandableSelectionView.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/view/bbcode/BBSpoilerView.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/LinkCheckDialog.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatFragment.kt`
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamPlayerManager.kt`

**Acceptance Criteria:**
- [ ] No `scaledDensity` references in `AndroidExtensions.kt`
- [ ] No `defaultDisplay.getMetrics()` in `DeviceUtils.kt`
- [ ] No `ViewCompat.animate(view)` calls (replaced with `view.animate()`)
- [ ] No `ViewCompat.isAttachedToWindow(view)` calls (replaced with `view.isAttachedToWindow`)
- [ ] No `ShareCompat.IntentBuilder.from(activity)` (replaced with `ShareCompat.IntentBuilder(context)`)
- [ ] No `InputMethodManager.SHOW_IMPLICIT` usages (replaced with `0`)
- [ ] `MasterKey.Builder` takes alias as second argument
- [ ] Media3 uses `C.CONTENT_TYPE_*` constants
- [ ] `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | wc -l` significantly reduced

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 2>&1 | grep "^w:" | wc -l` → count near zero (only @Suppress targets from Task 7 remain)

**Steps:**

- [ ] **Step 1: Fix `scaledDensity` in `AndroidExtensions.kt`**

Find lines ~71–72:
```kotlin
inline fun Context.sp(value: Int): Int = (value * resources.displayMetrics.scaledDensity).toInt()
inline fun Context.sp(value: Float): Int = (value * resources.displayMetrics.scaledDensity).toInt()
```

Replace with:
```kotlin
inline fun Context.sp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics).toInt()

inline fun Context.sp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics).toInt()
```

Add import: `import android.util.TypedValue`

- [ ] **Step 2: Fix `defaultDisplay.getMetrics()` in `DeviceUtils.kt`**

In both `getScreenWidth` and `getScreenHeight`, the `else` branch uses:
```kotlin
DisplayMetrics()
    .apply { windowManager.defaultDisplay.getMetrics(this) }
    .widthPixels
```

Replace with:
```kotlin
context.resources.displayMetrics.widthPixels
```
and:
```kotlin
context.resources.displayMetrics.heightPixels
```

(The `context` param is available in both functions.) Remove unused `import android.util.DisplayMetrics` if no longer needed after this change.

- [ ] **Step 3: Replace `ViewCompat.animate(view)` with `view.animate()`**

`view.animate()` (on `View`) returns a `ViewPropertyAnimator` — same API, not deprecated.

In `CommentsAdapter.kt` (lines ~210, ~216, ~223):
```kotlin
// Old:
ViewCompat.animate(expand).cancel()
ViewCompat.animate(expand).rotation(180f)
ViewCompat.animate(expand).rotation(0f)

// New:
expand.animate().cancel()
expand.animate().rotation(180f)
expand.animate().rotation(0f)
```

Apply the same replacement in:
- `NewsAdapter.kt` (lines ~138, ~144, ~151)
- `ProfileCommentAdapter.kt` (lines ~197, ~203, ~210)
- `ExpandableSelectionView.kt` (line ~151)
- `BBSpoilerView.kt` (lines ~124–125)
- `EditCommentFragment.kt` (line ~212)

Remove `import androidx.core.view.ViewCompat` from each file if it's no longer used for anything else.

- [ ] **Step 4: Replace `ViewCompat.isAttachedToWindow(view)` in `BBCodeView.kt`**

Find line ~75:
```kotlin
} else if (ViewCompat.isAttachedToWindow(this)) {
```

Replace with:
```kotlin
} else if (isAttachedToWindow) {
```

`isAttachedToWindow` is a property directly on `View` (API 19+, within minSdk 23).

- [ ] **Step 5: Fix `ShareCompat.IntentBuilder.from(activity)` in `LinkCheckDialog.kt`**

Find the `ShareCompat.IntentBuilder.from(requireActivity())` call. Replace with:
```kotlin
ShareCompat.IntentBuilder(requireContext())
```
The constructor taking a `Context` was added to replace the deprecated `from(Activity)`.

- [ ] **Step 6: Replace `SHOW_IMPLICIT` with `0`**

`InputMethodManager.SHOW_IMPLICIT` has value `0x0001`. The deprecation is the constant, not the `showSoftInput` method. Passing `0` (no flags) has near-identical behaviour for showing the soft keyboard.

In `MessengerFragment.kt` (line ~432):
```kotlin
?.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
```
→
```kotlin
?.showSoftInput(messageInput, 0)
```

In `ChatFragment.kt` (line ~403):
```kotlin
requireContext().getSystemService<InputMethodManager>()?.showSoftInput(messageInput, SHOW_IMPLICIT)
```
→
```kotlin
requireContext().getSystemService<InputMethodManager>()?.showSoftInput(messageInput, 0)
```
Remove `import android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT`.

In `EditCommentFragment.kt` (line ~342):
```kotlin
?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
```
→
```kotlin
?.showSoftInput(editor, 0)
```

- [ ] **Step 7: Fix `MasterKey.Builder` constructor in `MainModules.kt`**

Find line ~117:
```kotlin
val masterKey = MasterKey.Builder(androidContext())
```

Replace with:
```kotlin
val masterKey = MasterKey.Builder(androidContext(), MasterKey.DEFAULT_MASTER_KEY_ALIAS)
```

`DEFAULT_MASTER_KEY_ALIAS` is the string `"_androidx_security_master_key"` — using the constant is preferable.

- [ ] **Step 8: Fix Media3 stream type constants in `StreamPlayerManager.kt`**

Find lines ~293–303 using `C.TYPE_SS`, `C.TYPE_DASH`, `C.TYPE_HLS`, `C.TYPE_OTHER`. Replace:

```kotlin
C.TYPE_SS -> SsMediaSource.Factory(...).createMediaSource(mediaItem)
C.TYPE_DASH -> DashMediaSource.Factory(...).createMediaSource(mediaItem)
C.TYPE_HLS -> HlsMediaSource.Factory(...).createMediaSource(mediaItem)
C.TYPE_OTHER -> ProgressiveMediaSource.Factory(...).createMediaSource(mediaItem)
```

With:
```kotlin
C.CONTENT_TYPE_SS -> SsMediaSource.Factory(...).createMediaSource(mediaItem)
C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(...).createMediaSource(mediaItem)
C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(...).createMediaSource(mediaItem)
C.CONTENT_TYPE_UNKNOWN -> ProgressiveMediaSource.Factory(...).createMediaSource(mediaItem)
```

(`TYPE_OTHER` → `CONTENT_TYPE_UNKNOWN` is the Media3 mapping)

- [ ] **Step 9: Compile-check and fix any remaining issues**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

- [ ] **Step 10: Commit**

```bash
git add -u src/
git commit -m "fix: replace misc deprecated APIs (ViewCompat, scaledDensity, ShareCompat, Media3)"
```

---

### Task 7: @Suppress for no-compat deprecated APIs

**Goal:** Silence remaining compiler warnings for APIs where no compat replacement exists without significant refactoring.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamPlayerManager.kt` (CastPlayer)
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/TouchablePlayerView.kt` (getStreamTypeForAudioUsage)
- Modify: `src/main/kotlin/me/proxer/app/util/InAppUpdateFlow.kt` (startUpdateFlowForResult)
- Modify: `src/main/kotlin/me/proxer/app/ui/view/RatingDialog.kt` (neutralButton)
- Modify: `src/main/kotlin/me/proxer/app/settings/AboutFragment.kt` (LibsBuilder)
- Modify: `src/main/kotlin/me/proxer/app/anime/schedule/widget/ScheduleWidgetUpdateWorker.kt` (setRemoteAdapter)
- Modify: `src/main/kotlin/me/proxer/app/news/widget/NewsWidgetUpdateWorker.kt` (setRemoteAdapter)
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt` (EncryptedSharedPreferences if class is deprecated)

**Acceptance Criteria:**
- [ ] Zero `w:` lines from `./gradlew compileDebugKotlin --rerun-tasks` (cache-bypassing)
- [ ] All `@Suppress` annotations are targeted (function-level or expression-level, not file-level)

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks 2>&1 | grep "^w:" | wc -l` → `0`

**Steps:**

- [ ] **Step 1: Suppress Cast SDK deprecations in `StreamPlayerManager.kt`**

Find `buildCastPlayer` function (~line 333):
```kotlin
private fun buildCastPlayer(context: StreamActivity): CastPlayer? {
    return CastContext.getSharedInstance(context)
        ?.let { CastPlayer(it) }
        ?.apply { setSessionAvailabilityListener(castSessionAvailabilityListener) }
}
```

Add suppression:
```kotlin
@Suppress("DEPRECATION")
private fun buildCastPlayer(context: StreamActivity): CastPlayer? {
    return CastContext.getSharedInstance(context)
        ?.let { CastPlayer(it) }
        ?.apply { setSessionAvailabilityListener(castSessionAvailabilityListener) }
}
```

Also find the `setSessionAvailabilityListener(null)` call (~line 128) and add suppression at that call site:
```kotlin
@Suppress("DEPRECATION")
castPlayer?.setSessionAvailabilityListener(null)
```

- [ ] **Step 2: Suppress audio stream type in `TouchablePlayerView.kt`**

Find line ~45:
```kotlin
get() = Util.getStreamTypeForAudioUsage(player?.audioAttributes?.usage ?: C.USAGE_MEDIA)
```

Add:
```kotlin
@Suppress("DEPRECATION")
get() = Util.getStreamTypeForAudioUsage(player?.audioAttributes?.usage ?: C.USAGE_MEDIA)
```

- [ ] **Step 3: Suppress `startUpdateFlowForResult` in `InAppUpdateFlow.kt`**

Find the `appUpdateManager.startUpdateFlowForResult(...)` call (~line 63):
```kotlin
@Suppress("DEPRECATION")
appUpdateManager.startUpdateFlowForResult(
    appUpdateInfo,
    AppUpdateType.IMMEDIATE,
    activity,
    UPDATE_REQUEST_CODE
)
```

- [ ] **Step 4: Suppress `neutralButton` in `RatingDialog.kt`**

Find line ~46:
```kotlin
.neutralButton(R.string.dialog_rating_neutral)
```

Add function-level suppression on the enclosing function or expression-level:
```kotlin
@Suppress("DEPRECATION")
.neutralButton(R.string.dialog_rating_neutral)
```

- [ ] **Step 5: Suppress `LibsBuilder` in `AboutFragment.kt`**

Find line ~133:
```kotlin
LibsBuilder()
```

Add suppression on the surrounding expression or function:
```kotlin
@Suppress("DEPRECATION")
LibsBuilder()
    ...
    .start(requireContext())
```

- [ ] **Step 6: Suppress `setRemoteAdapter` in widget workers**

In `ScheduleWidgetUpdateWorker.kt` (line ~203) and `NewsWidgetUpdateWorker.kt` (line ~159):
```kotlin
@Suppress("DEPRECATION")
views.setRemoteAdapter(R.id.list, intent)
```

- [ ] **Step 7: Handle `EncryptedSharedPreferences` warnings in `MainModules.kt`**

Run the compiler and check if warnings for `EncryptedSharedPreferences`, `MasterKey.KeyScheme`, `PrefKeyEncryptionScheme`, and `PrefValueEncryptionScheme` remain after the `MasterKey.Builder` fix in Task 6.

If warnings remain (class-level deprecation), add file-level suppression ONLY for this file since there's no stable alternative API:
```kotlin
@file:Suppress("DEPRECATION")
```

Place as the first line (before `package`) in `MainModules.kt`.

- [ ] **Step 8: Final compile check — must be zero warnings**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks 2>&1 | grep "^w:"
```

Expected: no output at all.

If any warnings remain, identify and suppress or fix them before committing.

- [ ] **Step 9: Commit**

```bash
git add -u src/
git commit -m "fix: suppress unavoidable deprecations (Cast, AppUpdate, LibsBuilder, widgets)"
```

---

### Task 8: Final verification

**Goal:** Confirm all three quality gates pass with a clean build.

**Files:** None (verification only)

**Acceptance Criteria:**
- [ ] `ktlintCheck` exits 0 with no violations
- [ ] `compileDebugKotlin` emits zero `w:` lines (with `--rerun-tasks` to bypass cache)
- [ ] `assembleDebug` succeeds

**Verify:** All three commands succeed as described below.

**Steps:**

- [ ] **Step 1: Ktlint check**

```bash
./gradlew ktlintCheck --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

If it fails: the output names the file and line. Fix with `ktlintFormat` if it's a formatting issue, or read the rule name and fix manually.

- [ ] **Step 2: Kotlin compiler zero warnings**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2 --rerun-tasks 2>&1 | grep "^w:"
```

Expected: no output.

If lines appear: identify the deprecated symbol from the message, locate the call site, and either use the compat API or add a targeted `@Suppress("DEPRECATION")`.

- [ ] **Step 3: Full debug build**

```bash
./gradlew assembleDebug --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`. APK produced at `build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit (if any final fixes were made in this task)**

```bash
git add -u src/
git commit -m "fix: final warning cleanup verification passes"
```
