# Top App Bar Primary Color Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the top app bar render in the app's red primary accent on every screen, and fix `ProxerTheme` so derived Material3 color roles are correct in dark mode.

**Architecture:** Add one shared `ProxerTopAppBar` wrapper over Material3's `TopAppBar` that presets primary/onPrimary colors, then migrate all 29 in-app call sites to it (the video-player overlay bar is deliberately excluded). Separately, make `ProxerTheme` pick `darkColorScheme` vs `lightColorScheme` based on the active night mode instead of always using light.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose, Material3, Gradle 9.5.1 (`./gradlew`, JBR at `/opt/android-studio/jbr`).

**Spec:** `docs/superpowers/specs/2026-07-18-top-app-bar-primary-color-design.md`

---

## Context For The Implementer

You are working in an Android app that recently migrated from Fragments to Compose. There are no
Fragments left. Every screen is a `@Composable` that sets up a `Scaffold` with a `topBar = { ... }`
lambda.

**The bug:** Material3's `TopAppBar` defaults its `containerColor` to `colorScheme.surface` — white
in light mode, dark grey at night. No call site overrides it. Meanwhile the navigation drawer header
(`MainScreen.kt:104`) explicitly uses `colorScheme.primary`, the red `#8A0E0E`. So the drawer is red
and every bar it slides over is white. Nothing is wrong with the drawer; the bars just never opted in.

**Two facts that shape the work:**

1. **No call site passes `scrollBehavior` or `windowInsets`.** Verified by grep across the whole
   source tree. So the wrapper only needs `title`, `modifier`, `navigationIcon`, and `actions`.
   Do not add parameters "just in case" — YAGNI.
2. **`allWarningsAsErrors = false`** (`build.gradle:185`). After migration some screens may have an
   `@OptIn(ExperimentalMaterial3Api::class)` that is no longer strictly needed. **Leave those
   annotations alone.** Most of those screens use other experimental APIs too, and a redundant
   `@OptIn` is at worst a warning that cannot fail the build. Removing them risks breaking unrelated
   experimental usages for zero gain.

**Testing reality — read this so you do not go looking for a test to write.** The project's JVM
suite (`./gradlew testDebugUnitTest`, 345+ tests) is ViewModel/logic only. Compose tests live in
`src/androidTest/` and are instrumented — they need a running API 31+ device and cannot run in this
plan's automated gate. Compose also has no clean node assertion for "this bar's container is the
primary color" without screenshot testing, which this project does not have set up.

So: **do not write a new automated test for the color.** It would assert nothing real. Verification
for this change is (a) it compiles, (b) the existing JVM suite still passes as a regression check,
(c) a scripted grep gate proving no call site was missed, and (d) an on-device eyeball, which is
Task 7 and is the user's call.

---

## File Structure

**Created:**
- `src/main/kotlin/me/proxer/app/ui/compose/ProxerTopAppBar.kt` — the shared wrapper. Single
  responsibility: apply the app's bar colors to M3 `TopAppBar`. No logic, no state.

**Modified:**
- `src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt` — night-aware color scheme selection.
- 26 screen files — mechanical import swap + call rename. Listed per task below.

**Deliberately untouched:**
- `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt:374` — the video-player overlay bar
  keeps its translucent-black container. Task 1 adds a comment there recording why.

---

## Task 1: Add the `ProxerTopAppBar` wrapper

**Goal:** A shared top-bar composable that presets the app's primary/onPrimary colors, plus a comment
on the one call site that is intentionally excluded from using it.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/ui/compose/ProxerTopAppBar.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt:374`

**Acceptance Criteria:**
- [ ] `ProxerTopAppBar` exists in package `me.proxer.app.ui.compose`
- [ ] It sets `containerColor` to `colorScheme.primary` and all three content colors to `colorScheme.onPrimary`
- [ ] Its parameter list is exactly `title`, `modifier`, `navigationIcon`, `actions` — no more
- [ ] `StreamScreen.kt` carries a comment explaining why it does not use the wrapper
- [ ] `./gradlew compileDebugKotlin` succeeds

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Create the wrapper file**

Write `src/main/kotlin/me/proxer/app/ui/compose/ProxerTopAppBar.kt`:

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's standard top bar: the primary accent as the container, with [MaterialTheme]'s
 * `onPrimary` for the title and every icon slot.
 *
 * Material3's [TopAppBar] otherwise defaults to `colorScheme.surface`, which leaves the bar white in
 * light mode and grey at night while the navigation drawer header sits on the accent color.
 *
 * The only bar that legitimately skips this is the video-player overlay in `StreamScreen`, which
 * needs a translucent container to read over arbitrary video frames.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxerTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
```

- [ ] **Step 2: Comment the intentional exclusion in StreamScreen**

Open `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt`. Find line 374, which reads
`            TopAppBar(`. Insert a comment immediately above it, at the same indentation:

```kotlin
            // Not ProxerTopAppBar: this bar floats over the video, so it needs a translucent
            // container and white tints rather than the app's opaque accent color.
            TopAppBar(
```

Change nothing else in this file. Its `colors = TopAppBarDefaults.topAppBarColors(containerColor =
Color(0x80000000L.toInt()))` block at line 416 stays exactly as-is.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/ui/compose/ProxerTopAppBar.kt \
        src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt
git commit -m "feat(ui): add ProxerTopAppBar with primary accent colors"
```

---

## Task 2: Migrate the drawer-reachable main screens

**Goal:** The eight top bars on screens reachable directly from the navigation drawer use `ProxerTopAppBar`.

**Files (7 files, 8 call sites):**
- Modify: `src/main/kotlin/me/proxer/app/news/NewsScreen.kt:123`
- Modify: `src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt:66`
- Modify: `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt:235`
- Modify: `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt:289`
- Modify: `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt:99`
- Modify: `src/main/kotlin/me/proxer/app/settings/AboutScreen.kt:74` and `:237`
- Modify: `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt:396`

**Acceptance Criteria:**
- [ ] All 8 call sites call `ProxerTopAppBar` instead of `TopAppBar`
- [ ] Each file imports `me.proxer.app.ui.compose.ProxerTopAppBar`
- [ ] Each file's now-unused `import androidx.compose.material3.TopAppBar` is removed
- [ ] No existing `@OptIn(ExperimentalMaterial3Api::class)` annotation is removed
- [ ] `./gradlew compileDebugKotlin` succeeds

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Apply the same three-part edit to each file**

For every file in the Files list, the edit is identical in shape:

1. Remove the line `import androidx.compose.material3.TopAppBar`.
2. Add `import me.proxer.app.ui.compose.ProxerTopAppBar` in correct alphabetical position among the
   existing `me.proxer.app.*` imports.
3. Rename the call `TopAppBar(` → `ProxerTopAppBar(`.

**Do not change the arguments.** Every one of these call sites passes only `title`, `navigationIcon`,
and/or `actions`, all of which the wrapper accepts with identical semantics.

Worked example — `NewsScreen.kt`. Before:

```kotlin
import androidx.compose.material3.TopAppBar
```
```kotlin
            TopAppBar(
                title = { Text(stringResource(R.string.section_news)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
```

After — the `TopAppBar` import line is gone, `import me.proxer.app.ui.compose.ProxerTopAppBar` is
added among the `me.proxer.app.*` imports, and the call becomes:

```kotlin
            ProxerTopAppBar(
                title = { Text(stringResource(R.string.section_news)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
```

Note `AboutScreen.kt` has **two** call sites (lines 74 and 237) but only one import line to remove.

- [ ] **Step 2: Confirm no stragglers in these files**

```bash
grep -n "TopAppBar" src/main/kotlin/me/proxer/app/news/NewsScreen.kt \
  src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt \
  src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt \
  src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt \
  src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt \
  src/main/kotlin/me/proxer/app/settings/AboutScreen.kt \
  src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt
```
Expected: every hit says `ProxerTopAppBar`. No bare `TopAppBar` remains.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/news/NewsScreen.kt \
        src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt \
        src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt \
        src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt \
        src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt \
        src/main/kotlin/me/proxer/app/settings/AboutScreen.kt \
        src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt
git commit -m "fix(ui): use ProxerTopAppBar on drawer-reachable screens"
```

---

## Task 3: Migrate the chat and messaging screens

**Goal:** The nine top bars across the private/public chat surfaces use `ProxerTopAppBar`.

**Files (7 files, 9 call sites):**
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/conference/ConferenceScreen.kt:77` and `:96`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreen.kt:92`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerScreen.kt:212` and `:244`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreen.kt:114`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt:229` and `:249`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoScreen.kt:97`

**Acceptance Criteria:**
- [ ] All 9 call sites call `ProxerTopAppBar` instead of `TopAppBar`
- [ ] Each file imports `me.proxer.app.ui.compose.ProxerTopAppBar`
- [ ] Each file's now-unused `import androidx.compose.material3.TopAppBar` is removed
- [ ] No existing `@OptIn(ExperimentalMaterial3Api::class)` annotation is removed
- [ ] `./gradlew compileDebugKotlin` succeeds

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Apply the same three-part edit to each file**

Identical mechanical edit to Task 2, repeated here so this task stands alone:

1. Remove the line `import androidx.compose.material3.TopAppBar`.
2. Add `import me.proxer.app.ui.compose.ProxerTopAppBar` in correct alphabetical position among the
   existing `me.proxer.app.*` imports.
3. Rename each `TopAppBar(` call → `ProxerTopAppBar(`, leaving all arguments untouched.

Three files here have **two** call sites each but only one import line to remove:
`ConferenceScreen.kt`, `MessengerScreen.kt`, `ChatScreen.kt`. These screens typically swap between a
normal bar and a selection/action-mode bar, so both branches must be renamed — a partial edit leaves
a white bar appearing only when items are selected, which is easy to miss.

- [ ] **Step 2: Confirm no stragglers in these files**

```bash
grep -rn "TopAppBar" src/main/kotlin/me/proxer/app/chat/
```
Expected: every hit says `ProxerTopAppBar`. No bare `TopAppBar` remains.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/chat/
git commit -m "fix(ui): use ProxerTopAppBar on chat screens"
```

---

## Task 4: Migrate the media, profile and remaining detail screens

**Goal:** The final twelve top bars use `ProxerTopAppBar`, completing the migration.

**Files (12 files, 12 call sites):**
- Modify: `src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt:388`
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt:233`
- Modify: `src/main/kotlin/me/proxer/app/media/MediaScreen.kt:101`
- Modify: `src/main/kotlin/me/proxer/app/profile/ProfileScreen.kt:115`
- Modify: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreen.kt:187`
- Modify: `src/main/kotlin/me/proxer/app/notification/NotificationScreen.kt:168`
- Modify: `src/main/kotlin/me/proxer/app/forum/TopicScreen.kt:131`
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentScreen.kt:167`
- Modify: `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreen.kt:143`
- Modify: `src/main/kotlin/me/proxer/app/info/industry/IndustryScreen.kt:143`
- Modify: `src/main/kotlin/me/proxer/app/settings/status/ServerStatusScreen.kt:72`
- Modify: `src/main/kotlin/me/proxer/app/ui/WebViewScreen.kt:47`

**Acceptance Criteria:**
- [ ] All 12 call sites call `ProxerTopAppBar` instead of `TopAppBar`
- [ ] Each file imports `me.proxer.app.ui.compose.ProxerTopAppBar`
- [ ] Each file's now-unused `import androidx.compose.material3.TopAppBar` is removed
- [ ] No existing `@OptIn(ExperimentalMaterial3Api::class)` annotation is removed
- [ ] `StreamScreen.kt` still uses bare `TopAppBar` — it is NOT migrated
- [ ] `./gradlew compileDebugKotlin` succeeds

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Apply the same three-part edit to each file**

Identical mechanical edit to Tasks 2 and 3, repeated here so this task stands alone:

1. Remove the line `import androidx.compose.material3.TopAppBar`.
2. Add `import me.proxer.app.ui.compose.ProxerTopAppBar` in correct alphabetical position among the
   existing `me.proxer.app.*` imports.
3. Rename the `TopAppBar(` call → `ProxerTopAppBar(`, leaving all arguments untouched.

One file differs in shape: `ServerStatusScreen.kt:72` is a single-line call rather than a multi-line
one. Before:

```kotlin
            TopAppBar(title = { Text(stringResource(R.string.section_server_status)) })
```

After:

```kotlin
            ProxerTopAppBar(title = { Text(stringResource(R.string.section_server_status)) })
```

**`AnimeScreen.kt` and `MangaScreen.kt` sit in the same packages as `StreamScreen.kt`'s neighbours —
be careful not to touch `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt`.** It keeps its
bare `TopAppBar` deliberately.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt \
        src/main/kotlin/me/proxer/app/manga/MangaScreen.kt \
        src/main/kotlin/me/proxer/app/media/MediaScreen.kt \
        src/main/kotlin/me/proxer/app/profile/ \
        src/main/kotlin/me/proxer/app/notification/NotificationScreen.kt \
        src/main/kotlin/me/proxer/app/forum/TopicScreen.kt \
        src/main/kotlin/me/proxer/app/comment/EditCommentScreen.kt \
        src/main/kotlin/me/proxer/app/info/ \
        src/main/kotlin/me/proxer/app/settings/status/ServerStatusScreen.kt \
        src/main/kotlin/me/proxer/app/ui/WebViewScreen.kt
git commit -m "fix(ui): use ProxerTopAppBar on media and detail screens"
```

---

## Task 5: Enforce the migration with a grep gate

**Goal:** A scripted check that fails if any screen ever reintroduces a bare `TopAppBar`, so the
exclusion stays a deliberate one rather than drifting back into an oversight.

**Files:**
- Create: `scripts/check-top-app-bar.sh`

**Acceptance Criteria:**
- [ ] Script exits 0 on the current tree
- [ ] Script exits 1 if a bare `TopAppBar(` appears outside `StreamScreen.kt` / `ProxerTopAppBar.kt`
- [ ] Both branches are demonstrated to work before the task is closed

**Verify:** `./scripts/check-top-app-bar.sh` → exit 0, prints the OK line

**Steps:**

- [ ] **Step 1: Check whether a scripts directory already exists**

```bash
ls scripts/ 2>/dev/null || echo "no scripts dir yet"
```

If it does not exist, `mkdir -p scripts`.

- [ ] **Step 2: Write the script**

Write `scripts/check-top-app-bar.sh`:

```bash
#!/usr/bin/env bash
# Fails if any screen uses Material3's TopAppBar directly instead of ProxerTopAppBar.
# The only sanctioned exception is StreamScreen's translucent video-player overlay bar.
set -euo pipefail

offenders=$(grep -rn --include='*.kt' 'TopAppBar(' src/main/kotlin \
    | grep -v 'ProxerTopAppBar(' \
    | grep -v 'ui/compose/ProxerTopAppBar.kt' \
    | grep -v 'anime/stream/StreamScreen.kt' \
    || true)

if [ -n "$offenders" ]; then
    echo "Bare Material3 TopAppBar found. Use ProxerTopAppBar instead:" >&2
    echo "$offenders" >&2
    exit 1
fi

echo "OK: all top bars use ProxerTopAppBar (StreamScreen overlay excepted)."
```

Then: `chmod +x scripts/check-top-app-bar.sh`

- [ ] **Step 3: Verify the passing branch**

Run: `./scripts/check-top-app-bar.sh`
Expected: exit 0, prints `OK: all top bars use ProxerTopAppBar (StreamScreen overlay excepted).`

- [ ] **Step 4: Verify the failing branch actually fails**

A gate that cannot fail is not a gate. Prove it catches a regression:

```bash
sed -i 's/ProxerTopAppBar(/TopAppBar(/' src/main/kotlin/me/proxer/app/news/NewsScreen.kt
./scripts/check-top-app-bar.sh; echo "exit=$?"
git checkout -- src/main/kotlin/me/proxer/app/news/NewsScreen.kt
./scripts/check-top-app-bar.sh; echo "exit=$?"
```

Expected: first run prints the offender and `exit=1`; after the checkout, `exit=0`.
Confirm `git status --short` is clean for `NewsScreen.kt` before moving on.

- [ ] **Step 5: Commit**

```bash
git add scripts/check-top-app-bar.sh
git commit -m "chore: add grep gate enforcing ProxerTopAppBar usage"
```

---

## Task 6: Make `ProxerTheme` night-aware

**Goal:** `ProxerTheme` builds a dark color scheme in dark mode so derived Material3 roles stop
resolving to light-mode values, and passes the `onBackground` role it was silently dropping.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt`

**Acceptance Criteria:**
- [ ] `darkColorScheme(...)` is used when `isSystemInDarkTheme()` is true, `lightColorScheme(...)` otherwise
- [ ] Both branches pass the identical set of explicit colors
- [ ] `onBackground` is resolved from `R.attr.colorOnBackground` and passed to the scheme
- [ ] `./gradlew compileDebugKotlin` succeeds

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Understand why this works before editing**

`isSystemInDarkTheme()` reads the Configuration's `uiMode`. The app drives that through
`AppCompatDelegate.setDefaultNightMode(...)` from the user's theme-variant preference
(`MainApplication.kt:221`, values in `ThemeVariant.kt` — light / dark / follow-system /
auto-battery). So this composable follows the *app's* setting, not merely the OS setting. No extra
plumbing is needed.

The explicit colors need no change: they are resolved from theme attributes, and all three theme
variants (Classic, BlueGreen, Gloomy) already define those attrs DayNight-aware across
`values/styles.xml` and `values-night/styles.xml`. Only the *derived* roles were wrong.

- [ ] **Step 2: Rewrite the file**

Replace the contents of `src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt` with:

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.util.extension.resolveColor

@Composable
fun ProxerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val primary = Color(context.resolveColor(R.attr.colorPrimary))
    val onPrimary = Color(context.resolveColor(R.attr.colorOnPrimary))
    val secondary = Color(context.resolveColor(R.attr.colorSecondary))
    val onSecondary = Color(context.resolveColor(R.attr.colorOnSecondary))
    val background = Color(context.resolveColor(android.R.attr.colorBackground))
    val onBackground = Color(context.resolveColor(R.attr.colorOnBackground))
    val surface = Color(context.resolveColor(R.attr.colorSurface))
    val onSurface = Color(context.resolveColor(R.attr.colorOnSurface))
    val error = Color(context.resolveColor(R.attr.colorError))

    // The explicit colors below resolve correctly from theme attributes in both modes. Picking the
    // matching builder is what keeps the *derived* roles — surfaceVariant, surfaceContainer,
    // outline, scrim — from silently taking light-mode values while the app is in a dark theme.
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            error = error,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            error = error,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ProxerThemePreview() {
    ProxerTheme {
        Text("Preview")
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

If `R.attr.colorOnBackground` fails to resolve, do not invent a fallback color — stop and report it.
The attribute is declared in `values/styles.xml` under `Base.Theme.App.DayNight` and in
`values-night/styles.xml`, so a failure here means something else is wrong and guessing would hide it.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt
git commit -m "fix(ui): select dark color scheme in night mode"
```

---

## Task 7: Full verification sweep

**Goal:** Confirm the whole change compiles, passes the existing suite, satisfies the grep gate and
survives static analysis — and give the user a precise on-device checklist for the parts no
automated check can cover.

**Files:** None modified.

**Acceptance Criteria:**
- [ ] `./gradlew compileDebugKotlin` succeeds
- [ ] `./gradlew testDebugUnitTest` passes with no new failures
- [ ] `./scripts/check-top-app-bar.sh` exits 0
- [ ] `./gradlew detekt` reports no new findings
- [ ] The manual checklist below is written into the final report for the user

**Verify:** All four commands succeed in sequence.

**Steps:**

- [ ] **Step 1: Run the full gate**

Run each in order and capture the actual output — do not summarise a command you did not run:

```bash
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
./scripts/check-top-app-bar.sh
./gradlew detekt
```

Note from CLAUDE.md: never run `./gradlew test*` concurrently on this checkout — it corrupts
`build/test-results`. Run these strictly one after another. If results look corrupted,
`rm -rf build` and rerun.

- [ ] **Step 2: Confirm the exclusion survived**

```bash
grep -n "TopAppBar(" src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt
```
Expected: a bare `TopAppBar(` with the explanatory comment above it. If this now says
`ProxerTopAppBar`, the player overlay was migrated by mistake — revert that one call site.

- [ ] **Step 3: Report the manual checklist**

No automated check in this project can assert a rendered color. State this plainly rather than
implying the change is visually verified. Include this checklist in the final report:

- Open the drawer on the News screen — the bar and the drawer header should be the same red, with no
  seam between them.
- Visit a two-bar screen (a chat) and long-press to enter selection mode — the selection bar must be
  red too, not just the default one.
- Play a video (`StreamScreen`) — the overlay bar must stay translucent dark, NOT red.
- Switch Settings → theme variant to Dark and repeat the first two checks. Then also confirm ordinary
  surfaces (cards, dividers, sheets) look right, since Task 6 changed derived colors app-wide.
- Repeat once on the BlueGreen and Gloomy themes to confirm the accent tracks the selected theme
  rather than being hardcoded.

---

## Notes On Sequencing

Tasks 2, 3 and 4 all depend on Task 1 (the wrapper must exist), but are independent of each other —
they touch disjoint file sets. Task 5's gate only passes once all three are done. Task 6 is
independent of the whole migration and could run at any point; it is placed last-but-one so that
Task 7 verifies everything together.
