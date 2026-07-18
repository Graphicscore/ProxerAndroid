# Episode/Chapter Language Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stacked plain-text language labels in the episode/chapter list with side-by-side outlined buttons carrying flag icons and (for anime) hoster icons.

**Architecture:** Add a `@DrawableRes` flag-resource seam to `ProxerLibExtensions.kt` so Compose can use `painterResource`. Add a private `LanguageButton` composable to `EpisodeScreen.kt` and render the available languages in a wrapping `FlowRow`. Languages absent from the API are already never iterated, so "no button when unavailable" needs no explicit handling.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.06.01), Material3, Coil 2.7.0 (`AsyncImage`), JUnit4, Compose UI test (`androidx.compose.ui.test.junit4.v2`).

**Spec:** `docs/superpowers/specs/2026-07-18-episode-language-buttons-design.md`

**Baseline:** `./gradlew testDebugUnitTest` → 365 tests, 0 failures (recorded before any change).

---

### Correction to the spec

The spec's testing section lists a JVM test asserting `toAppDrawable(context)` still resolves after being rewritten. That call goes through `AppCompatResources.getDrawable`, which needs the Android framework and is not available in a plain JVM unit test. This plan keeps the pure `toAppDrawableRes()` mapping in the JVM suite (Task 1) and does not add a JVM test for the `Context`-taking overload. Its behaviour is unchanged — it is a one-line delegation — and it stays covered by every existing View-layer caller.

---

### Task 1: Flag resource helper

**Goal:** Add `Language.toAppDrawableRes()` returning a `@DrawableRes Int`, and make the existing `toAppDrawable(context)` delegate to it.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/extension/ProxerLibExtensions.kt:135-142`
- Create: `src/test/kotlin/me/proxer/app/util/extension/ProxerLibExtensionsTest.kt`

**Acceptance Criteria:**
- [ ] `Language.GERMAN.toAppDrawableRes()` returns `R.drawable.ic_germany`
- [ ] `Language.ENGLISH.toAppDrawableRes()` returns `R.drawable.ic_united_states`
- [ ] `Language.OTHER.toAppDrawableRes()` returns `R.drawable.ic_united_nations`
- [ ] `toAppDrawable(context)` no longer contains its own `when` — it calls `toAppDrawableRes()`
- [ ] Full JVM suite still green (365 existing + 4 new)

**Verify:** `./gradlew testDebugUnitTest --tests "me.proxer.app.util.extension.ProxerLibExtensionsTest"` → `BUILD SUCCESSFUL`, 4 tests passing

**Steps:**

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/me/proxer/app/util/extension/ProxerLibExtensionsTest.kt`:

```kotlin
package me.proxer.app.util.extension

import me.proxer.app.R
import me.proxer.library.enums.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProxerLibExtensionsTest {

    @Test fun `toAppDrawableRes maps german to the germany flag`() {
        assertEquals(R.drawable.ic_germany, Language.GERMAN.toAppDrawableRes())
    }

    @Test fun `toAppDrawableRes maps english to the united states flag`() {
        assertEquals(R.drawable.ic_united_states, Language.ENGLISH.toAppDrawableRes())
    }

    @Test fun `toAppDrawableRes maps other to the united nations flag`() {
        assertEquals(R.drawable.ic_united_nations, Language.OTHER.toAppDrawableRes())
    }

    @Test fun `toAppDrawableRes resolves a real resource for every language`() {
        Language.values().forEach { language ->
            assertNotEquals("No drawable for $language", 0, language.toAppDrawableRes())
        }
    }
}
```

This test file intentionally does NOT use `KoinTestRule`. `ProxerLibExtensionsTest` must stay free of `ErrorUtils`, which binds `StorageHelper`/`PreferenceHelper` via `by lazy` once per JVM and poisons sibling tests (see CLAUDE.md).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.util.extension.ProxerLibExtensionsTest"`
Expected: FAIL — compilation error, `Unresolved reference: toAppDrawableRes`

- [ ] **Step 3: Add the helper and delegate**

In `src/main/kotlin/me/proxer/app/util/extension/ProxerLibExtensions.kt`, add this import alongside the existing `androidx.appcompat.content.res.AppCompatResources` import (imports are alphabetically sorted — `androidx.annotation.DrawableRes` sorts before `androidx.appcompat`):

```kotlin
import androidx.annotation.DrawableRes
```

Then replace the existing block at lines 135-142:

```kotlin
fun Language.toAppDrawable(context: Context) = AppCompatResources.getDrawable(
    context,
    when (this) {
        Language.GERMAN -> R.drawable.ic_germany
        Language.ENGLISH -> R.drawable.ic_united_states
        Language.OTHER -> R.drawable.ic_united_nations
    },
) ?: error("Could not resolve Drawable for language: $this")
```

with:

```kotlin
@DrawableRes
fun Language.toAppDrawableRes() = when (this) {
    Language.GERMAN -> R.drawable.ic_germany
    Language.ENGLISH -> R.drawable.ic_united_states
    Language.OTHER -> R.drawable.ic_united_nations
}

fun Language.toAppDrawable(context: Context) = AppCompatResources.getDrawable(context, toAppDrawableRes())
    ?: error("Could not resolve Drawable for language: $this")
```

Leave the `Country.toAppDrawable` function immediately below untouched — it is a different enum and out of scope.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.util.extension.ProxerLibExtensionsTest"`
Expected: PASS, 4 tests

- [ ] **Step 5: Run the full suite to confirm nothing regressed**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 369 tests, 0 failures

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/util/extension/ProxerLibExtensions.kt src/test/kotlin/me/proxer/app/util/extension/ProxerLibExtensionsTest.kt
git commit -m "feat: add Language.toAppDrawableRes for Compose flag icons"
```

---

### Task 2: `LanguageButton` composable and `FlowRow` wiring

**Goal:** Render each available language as an outlined button with a flag icon and, for anime, its hoster icons — laid out side by side and wrapping.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt:206-239` (expanded section), plus imports

**Acceptance Criteria:**
- [ ] Expanding an episode row shows one `OutlinedButton` per available language, arranged horizontally
- [ ] Each button shows the flag at 16dp followed by the language label
- [ ] Anime buttons whose language has hoster images show a `VerticalDivider` then one 18dp `AsyncImage` per hoster
- [ ] Manga/novel buttons show neither divider nor hoster icons (`hosterImages` is `null` there)
- [ ] Buttons wrap to a second line rather than overflowing when four languages are present
- [ ] Tapping a button navigates exactly as before (same `AnimeActivity`/`MangaActivity` arguments)
- [ ] `./gradlew detekt` reports no new findings

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add the required imports**

In `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt`, add to the existing import block (keep alphabetical ordering — ktlint enforces it):

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import me.proxer.app.util.extension.toAppDrawableRes
import me.proxer.library.util.ProxerUrls
```

Keep the existing `androidx.compose.foundation.clickable` import — the bookmark dialog still uses it.

- [ ] **Step 2: Add the `LanguageButton` composable**

Append to `EpisodeScreen.kt`, after the `EpisodeItem` function and before `EpisodeContentPreview`:

```kotlin
@Composable
private fun LanguageButton(language: MediaLanguage, hosterImages: List<String>?, onClick: () -> Unit) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Image(
            painter = painterResource(language.toGeneralLanguage().toAppDrawableRes()),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = language.toAppString(context),
            style = MaterialTheme.typography.labelLarge,
        )

        if (!hosterImages.isNullOrEmpty()) {
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .height(16.dp),
            )

            hosterImages.forEach { hosterImage ->
                AsyncImage(
                    model = ProxerUrls.hosterImage(hosterImage).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
```

`contentDescription = null` on both images is deliberate: the label `Text` sits inside the same button and already carries the meaning, so a description would produce a duplicate announcement. This matches every non-TV screen in the codebase.

- [ ] **Step 3: Extract the navigation logic**

Append to `EpisodeScreen.kt`, after `LanguageButton`:

```kotlin
private fun navigateToEpisode(
    activity: Activity,
    episode: EpisodeRow,
    language: MediaLanguage,
    mediaId: String,
    mediaName: String?,
) = when (episode.category) {
    Category.ANIME -> AnimeActivity.navigateTo(
        activity,
        mediaId,
        episode.number,
        language.toAnimeLanguage(),
        mediaName,
        episode.episodeAmount,
    )

    Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
        activity,
        mediaId,
        episode.number,
        language.toGeneralLanguage(),
        episode.title,
        mediaName,
        episode.episodeAmount,
    )
}
```

- [ ] **Step 4: Replace the expanded section**

In `EpisodeItem`, replace the whole `if (expanded) { ... }` block at lines 206-239:

```kotlin
        if (expanded) {
            episode.languageHosterList.forEach { (language, _) ->
                Text(
                    text = language.toAppString(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activity ?: return@clickable
                            when (episode.category) {
                                Category.ANIME -> AnimeActivity.navigateTo(
                                    activity,
                                    mediaId,
                                    episode.number,
                                    language.toAnimeLanguage(),
                                    mediaName,
                                    episode.episodeAmount,
                                )

                                Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                                    activity,
                                    mediaId,
                                    episode.number,
                                    language.toGeneralLanguage(),
                                    episode.title,
                                    mediaName,
                                    episode.episodeAmount,
                                )
                            }
                        }
                        .padding(top = 4.dp, start = 8.dp),
                )
            }
        }
```

with:

```kotlin
        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                episode.languageHosterList.forEach { (language, hosterImages) ->
                    LanguageButton(
                        language = language,
                        hosterImages = hosterImages,
                        onClick = {
                            if (activity != null) {
                                navigateToEpisode(activity, episode, language, mediaId, mediaName)
                            }
                        },
                    )
                }
            }
        }
```

Note the destructuring now binds `hosterImages` instead of discarding it with `_` — that is the piece the Compose migration dropped.

After this edit, the `context` val at the top of `EpisodeItem` may become unused (the label lookup moved into `LanguageButton`). Check whether `context` is still referenced inside `EpisodeItem`; if it is not, delete the `val context = LocalContext.current` line to avoid a compiler warning. `val activity = context as? Activity` still needs it, so most likely both stay — verify rather than assume.

- [ ] **Step 5: Compile and lint**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`, no new detekt findings

- [ ] **Step 6: Run the unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 369 tests, 0 failures

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt
git commit -m "feat(episode): show languages as flag buttons with hoster icons"
```

---

### Task 3: Flags in the bookmark language dialog

**Goal:** Show the same flag icon beside each language in the bookmark language picker, so the two lists look consistent.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt:117-135` (the `AlertDialog` text block)

**Acceptance Criteria:**
- [ ] Long-pressing an episode opens the bookmark dialog with a flag before each language label
- [ ] Flags render at 20dp (larger than the 16dp in buttons, to match the dialog's `bodyLarge` text)
- [ ] No hoster icons appear in the dialog
- [ ] Tapping a row still calls `onBookmark` with the same arguments and dismisses the dialog

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Replace the dialog's language list**

In `EpisodeContent`, inside the `AlertDialog`'s `text = { Column { ... } }` block, replace:

```kotlin
                    episodeToBookmark.languageHosterList.forEach { (language, _) ->
                        Text(
                            text = language.toAppString(context),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBookmark(
                                        episodeToBookmark.number,
                                        language,
                                        episodeToBookmark.category,
                                    )
                                    bookmarkEpisode = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
```

with:

```kotlin
                    episodeToBookmark.languageHosterList.forEach { (language, _) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBookmark(
                                        episodeToBookmark.number,
                                        language,
                                        episodeToBookmark.category,
                                    )
                                    bookmarkEpisode = null
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Image(
                                painter = painterResource(language.toGeneralLanguage().toAppDrawableRes()),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = language.toAppString(context),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
```

The `clickable` modifier deliberately stays on the `Row` rather than moving to the `Text`, so the whole row including the flag remains one tap target.

All imports this needs (`Image`, `painterResource`, `Spacer`, `width`, `size`, `toAppDrawableRes`) were already added in Task 2. `Row` and `Alignment` are already imported in this file.

- [ ] **Step 2: Compile and lint**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt
git commit -m "feat(episode): show flags in the bookmark language dialog"
```

---

### Task 4: Collapse the duplicate flag mapping in `MediaListScreen`

**Goal:** Point the inline flag mapping in `MediaListScreen` at the new shared helper so the drawable choice lives in one place.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt:425-439`

**Acceptance Criteria:**
- [ ] `MediaListScreen` no longer references `R.drawable.ic_germany` or `R.drawable.ic_united_states` directly
- [ ] The rendered output is unchanged — same two conditional 16dp flags, same `contentDescription` strings
- [ ] `./gradlew compileDebugKotlin detekt` passes

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`, and `grep -c 'R.drawable.ic_germany' src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt` → `0`

**Steps:**

- [ ] **Step 1: Add the import**

Add to `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt` (alphabetical position among the other `me.proxer.app.util.extension.*` imports):

```kotlin
import me.proxer.app.util.extension.toAppDrawableRes
```

- [ ] **Step 2: Replace the inline mapping**

Replace lines 425-439:

```kotlin
                val languages = entry.languages.map { it.toGeneralLanguage() }.distinct()
                if (languages.contains(Language.GERMAN)) {
                    Image(
                        painter = painterResource(R.drawable.ic_germany),
                        contentDescription = stringResource(R.string.language_german),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (languages.contains(Language.ENGLISH)) {
                    Image(
                        painter = painterResource(R.drawable.ic_united_states),
                        contentDescription = stringResource(R.string.language_english),
                        modifier = Modifier.size(16.dp),
                    )
                }
```

with:

```kotlin
                val languages = entry.languages.map { it.toGeneralLanguage() }.distinct()
                if (languages.contains(Language.GERMAN)) {
                    Image(
                        painter = painterResource(Language.GERMAN.toAppDrawableRes()),
                        contentDescription = stringResource(R.string.language_german),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (languages.contains(Language.ENGLISH)) {
                    Image(
                        painter = painterResource(Language.ENGLISH.toAppDrawableRes()),
                        contentDescription = stringResource(R.string.language_english),
                        modifier = Modifier.size(16.dp),
                    )
                }
```

The two explicit `if` branches are kept on purpose. Rewriting this as a loop over `languages` would also render a flag for `Language.OTHER`, which is a behaviour change this task does not want. Only the drawable lookup moves to the shared helper.

- [ ] **Step 3: Verify no direct drawable references remain**

Run: `grep -n 'R.drawable.ic_germany\|R.drawable.ic_united_states' src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt`
Expected: no output

- [ ] **Step 4: Compile and lint**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

If `R` or `painterResource` became unused after this edit, remove the stale import. `R` is almost certainly still used (`R.string.language_german` remains) — check rather than assume.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt
git commit -m "refactor(media): reuse toAppDrawableRes for media list flags"
```

---

### Task 5: Compose UI test for button rendering

**Goal:** Assert that exactly the available languages get a button, and absent ones get none.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt` (widen `EpisodeContent` from `private` to `internal`)
- Create: `src/androidTest/kotlin/me/proxer/app/media/episode/EpisodeScreenTest.kt`

**Acceptance Criteria:**
- [ ] A row built with `GERMAN_SUB` + `ENGLISH_SUB` shows both labels after expanding
- [ ] A row built with only `GERMAN_SUB` shows that label and NOT the English one
- [ ] Language labels are absent before the row is expanded
- [ ] Test class runs green on an API 31+ emulator

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.episode.EpisodeScreenTest` → `BUILD SUCCESSFUL`, 3 tests passing

⚠️ **This verification requires a running API 31+ emulator or device.** Per CLAUDE.md, mockk-android's agent instruments `Object.toString()` process-wide and Espresso reflects over an API 31+ class, so API 30 and below crash the whole instrumentation process. If no such device is attached, report the task as implemented-but-unverified rather than claiming a pass — do NOT substitute a JVM run as evidence.

**Steps:**

- [ ] **Step 1: Make `EpisodeContent` reachable from the test**

In `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt`, change the declaration at line 83:

```kotlin
private fun EpisodeContent(
```

to:

```kotlin
internal fun EpisodeContent(
```

Leave the `@Composable` annotation and every parameter untouched. This mirrors `TvEpisodeScreenContent`, which is likewise exposed so `TvEpisodeScreenTest` can drive it directly.

- [ ] **Step 2: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/media/episode/EpisodeScreenTest.kt`:

```kotlin
package me.proxer.app.media.episode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.library.entity.anime.AnimeEpisode
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun episodeRow(vararg languages: MediaLanguage) = EpisodeRow(
        category = Category.ANIME,
        userProgress = null,
        episodeAmount = 12,
        episodes = languages.map { language ->
            AnimeEpisode(
                number = 1,
                language = language,
                hosters = emptySet(),
                hosterImages = emptyList(),
            )
        },
    )

    private fun setContent(row: EpisodeRow) {
        composeTestRule.setContent {
            ProxerTheme {
                EpisodeContent(
                    data = listOf(row),
                    error = null,
                    isLoading = false,
                    mediaId = "1",
                    mediaName = "Test Media",
                    bookmarkResult = MutableLiveData(null),
                    bookmarkError = MutableLiveData(null),
                    onRetry = {},
                    onBookmark = { _, _, _ -> },
                )
            }
        }
    }

    private val episodeTitle get() = Category.ANIME.toEpisodeAppString(context, 1)
    private val germanSub get() = context.getString(R.string.language_german_sub)
    private val englishSub get() = context.getString(R.string.language_english_sub)

    @Test fun `language_buttons_are_hidden_until_the_row_is_expanded`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB, MediaLanguage.ENGLISH_SUB))

        composeTestRule.onNodeWithText(germanSub).assertDoesNotExist()
        composeTestRule.onNodeWithText(englishSub).assertDoesNotExist()
    }

    @Test fun `expanding_shows_one_button_per_available_language`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB, MediaLanguage.ENGLISH_SUB))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onNodeWithText(germanSub).assertIsDisplayed()
        composeTestRule.onNodeWithText(englishSub).assertIsDisplayed()
    }

    @Test fun `an_unavailable_language_gets_no_button`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onNodeWithText(germanSub).assertIsDisplayed()
        composeTestRule.onNodeWithText(englishSub).assertDoesNotExist()
    }
}
```

`assertDoesNotExist()` is an extension on `SemanticsNodeInteraction` and needs no separate import beyond the ones listed.

- [ ] **Step 3: Check for an attached device**

Run: `adb devices`
Expected: at least one `device` entry whose API level is 31 or higher (`adb shell getprop ro.build.version.sdk`).

If none is attached, stop here, commit the test, and report it as unverified.

- [ ] **Step 4: Run the instrumented test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.episode.EpisodeScreenTest`
Expected: `BUILD SUCCESSFUL`, 3 tests, 0 failures

Note `connectedDebugAndroidTest` does NOT accept `--tests` — the `-P` form above is the only filter that works here.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt src/androidTest/kotlin/me/proxer/app/media/episode/EpisodeScreenTest.kt
git commit -m "test(episode): cover language button rendering"
```

---

## Final verification

- [ ] `./gradlew testDebugUnitTest` → 369 tests, 0 failures
- [ ] `./gradlew detekt` → `BUILD SUCCESSFUL`
- [ ] `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] Manual check on a device: open any anime, Episodes tab, expand a row — flags and hoster icons appear side by side; open any manga, expand a chapter — flags appear, no hoster icons, no divider
