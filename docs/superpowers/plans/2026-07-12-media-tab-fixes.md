# Media Tab Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two Compose-migration regressions: the Anime/Manga drawer tabs get stuck showing whichever category was opened first, and the manga detail screen mislabels its episodes tab "Episoden" instead of "Kapitel".

**Architecture:** Both fixes extract a small pure, unit-testable function that the existing composable then calls: `mediaListViewModelKey(category)` supplies a category-scoped Koin `key` so `MediaListScreen` stops sharing one cached `MediaListViewModel` across categories; `episodeTabTitleRes(category)` picks the correct string resource, and `category` is threaded from `MediaActivity` (which already resolves it from the intent extra) through `MediaScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Koin 4.2.1 (`koinViewModel`), JUnit 4 (JVM unit tests, no Robolectric/Compose-test needed — both new functions are plain, non-`@Composable` top-level functions).

## Global Constraints

- Default/fallback category (anime, or `null`) must preserve current behavior exactly — no regression for existing anime users.
- No new test infrastructure — use plain `org.junit.Assert`-based JVM tests, matching existing `src/test/kotlin` conventions (see `MediaListViewModelTest.kt`).
- Don't touch `MainScreen.kt` drawer selection logic — confirmed correct, out of scope.
- Don't add Compose UI test infra — not present for these screens outside the `tv-support` branch.

---

### Task 1: Category-scoped ViewModel key for `MediaListScreen`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt:101-119`
- Test: `src/test/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt` (new)

**Interfaces:**
- Produces: `internal fun mediaListViewModelKey(category: Category): String` in `me.proxer.app.media.list` package — returns a string unique per `Category` value, stable for repeated calls with the same category.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt`:

```kotlin
package me.proxer.app.media.list

import me.proxer.library.enums.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaListScreenTest {

    @Test
    fun `mediaListViewModelKey differs between anime and manga`() {
        assertNotEquals(mediaListViewModelKey(Category.ANIME), mediaListViewModelKey(Category.MANGA))
    }

    @Test
    fun `mediaListViewModelKey is deterministic per category`() {
        assertEquals(mediaListViewModelKey(Category.ANIME), mediaListViewModelKey(Category.ANIME))
        assertEquals(mediaListViewModelKey(Category.MANGA), mediaListViewModelKey(Category.MANGA))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.list.MediaListScreenTest"`
Expected: FAIL — compile error, `mediaListViewModelKey` is unresolved.

- [ ] **Step 3: Write minimal implementation**

In `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt`, add a top-level function right before `fun MediaListScreen(...)` (i.e. above line 101):

```kotlin
internal fun mediaListViewModelKey(category: Category): String = "media_list_${category.name}"
```

Then change lines 104-119 from:

```kotlin
    val viewModel = koinViewModel<MediaListViewModel> {
        parametersOf(
            MediaSearchSortCriteria.RATING,
            defaultType,
            null as String?,
            null as Language?,
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            enumSetOf<FskConstraint>(),
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            TagRateFilter.RATED_ONLY,
            TagSpoilerFilter.NO_SPOILERS,
            false as Boolean?,
        )
    }
```

to:

```kotlin
    val viewModel = koinViewModel<MediaListViewModel>(
        key = mediaListViewModelKey(category),
    ) {
        parametersOf(
            MediaSearchSortCriteria.RATING,
            defaultType,
            null as String?,
            null as Language?,
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            enumSetOf<FskConstraint>(),
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            TagRateFilter.RATED_ONLY,
            TagSpoilerFilter.NO_SPOILERS,
            false as Boolean?,
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.list.MediaListScreenTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Verify the module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt src/test/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt
git commit -m "fix(media): key MediaListScreen's ViewModel per category

Anime and Manga tabs shared one cached MediaListViewModel because
koinViewModel() had no key, so whichever category loaded first stuck.
Key it like ProfileMediaListScreen already does."
```

---

### Task 2: Correct episode/chapter tab label on `MediaScreen`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/MediaScreen.kt:1-57`
- Modify: `src/main/kotlin/me/proxer/app/media/MediaActivity.kt:76-85`
- Test: `src/test/kotlin/me/proxer/app/media/MediaScreenTest.kt` (new)

**Interfaces:**
- Consumes: `MediaActivity.category: Category?` (existing property, `src/main/kotlin/me/proxer/app/media/MediaActivity.kt:57-58`).
- Produces: `internal fun episodeTabTitleRes(category: Category?): Int` in `me.proxer.app.media` package — returns `R.string.category_manga_episodes_title` when `category == Category.MANGA`, else `R.string.category_anime_episodes_title`. `MediaScreen` gains a `category: Category? = null` parameter.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/me/proxer/app/media/MediaScreenTest.kt`:

```kotlin
package me.proxer.app.media

import me.proxer.app.R
import me.proxer.library.enums.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaScreenTest {

    @Test
    fun `episodeTabTitleRes returns chapters title for manga`() {
        assertEquals(R.string.category_manga_episodes_title, episodeTabTitleRes(Category.MANGA))
    }

    @Test
    fun `episodeTabTitleRes returns episodes title for anime`() {
        assertEquals(R.string.category_anime_episodes_title, episodeTabTitleRes(Category.ANIME))
    }

    @Test
    fun `episodeTabTitleRes falls back to episodes title for null category`() {
        assertEquals(R.string.category_anime_episodes_title, episodeTabTitleRes(null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.MediaScreenTest"`
Expected: FAIL — compile error, `episodeTabTitleRes` is unresolved.

- [ ] **Step 3: Write minimal implementation**

In `src/main/kotlin/me/proxer/app/media/MediaScreen.kt`:

Add the import (alongside the existing imports, e.g. after line 28 `import kotlinx.coroutines.launch`):

```kotlin
import me.proxer.library.enums.Category
```

Add a top-level function right before `fun MediaScreen(...)` (i.e. above line 42):

```kotlin
internal fun episodeTabTitleRes(category: Category?): Int = when (category) {
    Category.MANGA -> R.string.category_manga_episodes_title
    else -> R.string.category_anime_episodes_title
}
```

Change the signature on line 42 from:

```kotlin
fun MediaScreen(id: String, name: String, initialTab: Int = 0, onBack: () -> Unit) {
```

to:

```kotlin
fun MediaScreen(id: String, name: String, category: Category? = null, initialTab: Int = 0, onBack: () -> Unit) {
```

Change the `tabs` list (lines 50-57) from:

```kotlin
    val tabs = listOf(
        R.string.section_media_info,
        R.string.section_comments,
        R.string.category_anime_episodes_title,
        R.string.section_relations,
        R.string.section_recommendations,
        R.string.section_discussions,
    )
```

to:

```kotlin
    val tabs = listOf(
        R.string.section_media_info,
        R.string.section_comments,
        episodeTabTitleRes(category),
        R.string.section_relations,
        R.string.section_recommendations,
        R.string.section_discussions,
    )
```

Then, in `src/main/kotlin/me/proxer/app/media/MediaActivity.kt`, change the `MediaScreen(...)` call (lines 78-84) from:

```kotlin
                MediaScreen(
                    id = id,
                    name = name ?: "",
                    initialTab = initialTab,
                    onBack = { finish() },
                )
```

to:

```kotlin
                MediaScreen(
                    id = id,
                    name = name ?: "",
                    category = category,
                    initialTab = initialTab,
                    onBack = { finish() },
                )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.MediaScreenTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Verify the module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/MediaScreen.kt src/main/kotlin/me/proxer/app/media/MediaActivity.kt src/test/kotlin/me/proxer/app/media/MediaScreenTest.kt
git commit -m "fix(media): show correct chapters/episodes tab label for manga

MediaScreen hardcoded the anime 'Episoden' string regardless of
category. MediaActivity already resolved category from the intent
extra but never passed it through; thread it in and pick the right
string resource."
```

---

### Task 3: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions in `MediaListViewModelTest`, `MediaInfoViewModelTest`, `ProfileMediaListViewModelTest`, or the two new test files).

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL — no new findings in the four modified files.

- [ ] **Step 3: Manual smoke test (if a connected device/emulator is available)**

Run: `./gradlew installDebug`
Then in the app: open the drawer, tap Anime, then tap Manga, then tap Anime again — confirm each shows its own list, not a stuck copy of the first. Open a manga entry — confirm its tab bar reads "Kapitel", not "Episoden". Open an anime entry — confirm it still reads "Episoden".

If no device is connected, state explicitly that this step was skipped rather than claiming it passed.
