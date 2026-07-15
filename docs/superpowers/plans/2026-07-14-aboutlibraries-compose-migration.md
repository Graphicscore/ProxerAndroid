# AboutLibraries Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the deprecated `com.mikepenz:aboutlibraries` Activity-launch licenses flow in `AboutScreen.kt` with the Compose-native `LibrariesContainer` from `aboutlibraries-compose-m3` 15.0.3.

**Architecture:** Bump `aboutLibrariesVersion` to `15.0.3` (drives both the Gradle plugin and the runtime dependency), swap the runtime dependency from the discontinued legacy `com.mikepenz:aboutlibraries` artifact (not published past `14.2.1`) to `com.mikepenz:aboutlibraries-compose` + `com.mikepenz:aboutlibraries-compose-m3`, and replace the `LibsBuilder().start(activity)` call with a local `showLicenses` state toggle in `AboutScreen` that swaps in a small `LicensesScreen` composable — following the project's existing `onBack: () -> Unit` + `ArrowBack` navigation convention (see `AnimeScreen.kt:390-406`) since there is no Compose Navigation library in this project.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), `com.mikepenz:aboutlibraries-compose-m3:15.0.3`, `com.mikepenz:aboutlibraries-compose:15.0.3` (for the `produceLibraries()` loader), `androidx.activity.compose.BackHandler`.

## Global Constraints

- `aboutLibrariesVersion` bumps to `15.0.3` (spec: latest upstream release).
- The legacy `com.mikepenz:aboutlibraries` artifact must be removed — it is not published past `14.2.1` and no longer contains `LibsBuilder`/Activity APIs at `15.0.3`.
- No new Gradle plugin configuration block is needed — the AboutLibraries plugin's default behavior (no explicit `aboutLibraries { }` block exists today) continues to generate `R.raw.aboutlibraries` per variant automatically.
- No Compose Navigation library is to be introduced. Follow the existing `onBack: () -> Unit` lambda + `TopAppBar` `navigationIcon` with `Icons.AutoMirrored.Filled.ArrowBack` convention already used across the codebase (e.g. `AnimeScreen.kt`, `ConferenceScreen.kt`).
- Reuse the existing `R.string.about_licenses_activity_title` string resource for the new screen's title — no new strings.
- No ViewModel or business logic is introduced; this is a UI-only change. No new unit tests apply — verification is via compilation + manual in-app check.
- `aboutlibraries-compose-m3-android:15.0.3`'s runtime variant depends on `com.github.skydoves:compose-stability-runtime:0.10.0`, which is published on Maven Central (via Sonatype), not JitPack. This project's `gradle/repositories.gradle` currently excludes all `com.github.*` groups from Maven Central except an allowlist (line 20), and routes all `com.github.*` to JitPack (line 40) — JitPack has no such artifact for `skydoves` (it publishes directly to Central), so resolution fails unless `skydoves` is added to the Maven Central allowlist.

---

### Task 1: Migrate AboutLibraries dependency and AboutScreen to Compose

**Files:**
- Modify: `gradle/repositories.gradle:20`
- Modify: `gradle/versions.gradle:64`
- Modify: `gradle/dependencies.gradle:53`
- Modify: `src/main/kotlin/me/proxer/app/settings/AboutScreen.kt`

**Interfaces:**
- Produces: `LicensesScreen(onBack: () -> Unit)` — a private composable in `AboutScreen.kt`, the project's licenses sub-screen. No other file calls it; `AboutScreen` is its only caller.

- [ ] **Step 0: Allow Maven Central to serve `com.github.skydoves` artifacts**

`aboutlibraries-compose-m3-android:15.0.3` transitively requires
`com.github.skydoves:compose-stability-runtime:0.10.0`. That artifact is
published on Maven Central (confirmed: `https://repo1.maven.org/maven2/com/github/skydoves/compose-stability-runtime/0.10.0/compose-stability-runtime-0.10.0.pom`
returns 200), not on JitPack — but `gradle/repositories.gradle` currently
routes every `com.github.*` group to JitPack only, except a small allowlist
that's still permitted to resolve from Maven Central.

In `gradle/repositories.gradle:20`, change:

```groovy
            excludeGroupByRegex "com\\.github\\.(?!bumptech|rubensousa|shyiko|anrwatchdog|pengrad|ajalt).*"
```

to:

```groovy
            excludeGroupByRegex "com\\.github\\.(?!bumptech|rubensousa|shyiko|anrwatchdog|pengrad|ajalt|skydoves).*"
```

This is additive only: it lets Maven Central also be tried for
`com.github.skydoves:*` artifacts. The existing JitPack repository
(`gradle/repositories.gradle:37-41`) still matches `com.github.*` too, so if
a future `skydoves` artifact isn't on Central, resolution still falls
through to JitPack as before.

- [ ] **Step 1: Bump the version**

In `gradle/versions.gradle:64`, change:

```groovy
    aboutLibrariesVersion = "14.2.1"
```

to:

```groovy
    aboutLibrariesVersion = "15.0.3"
```

- [ ] **Step 2: Swap the dependency**

In `gradle/dependencies.gradle:53`, change:

```groovy
    implementation "com.mikepenz:aboutlibraries:$aboutLibrariesVersion"
```

to:

```groovy
    implementation "com.mikepenz:aboutlibraries-compose:$aboutLibrariesVersion"
    implementation "com.mikepenz:aboutlibraries-compose-m3:$aboutLibrariesVersion"
```

(`aboutlibraries-compose` provides the Android `produceLibraries()` loader; `aboutlibraries-compose-m3` provides the Material3 `LibrariesContainer`. Both pull in `aboutlibraries-core` transitively.)

- [ ] **Step 3: Confirm the expected compile failure**

Run: `./gradlew compileDebugKotlin`

Expected: FAIL — `AboutScreen.kt` still imports `com.mikepenz.aboutlibraries.LibsBuilder`, which no longer resolves now that the legacy artifact is gone. This confirms the dependency swap took effect and that Step 4 is necessary before the build is green again.

- [ ] **Step 4: Rewrite AboutScreen.kt**

Replace the full contents of `src/main/kotlin/me/proxer/app/settings/AboutScreen.kt` with:

```kotlin
package me.proxer.app.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.getSystemService
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import me.proxer.app.BuildConfig
import me.proxer.app.R
import me.proxer.app.settings.status.ServerStatusActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.startActivityOrToast
import me.proxer.app.util.extension.toast

private const val SUPPORT_PROXER_MAIL = "appsupport@proxer.de"
private const val DEVELOPER_GITHUB_NAME = "rubengees"

private const val TEAM_URL = "https://proxer.me/team?device=default"
private const val FACEBOOK_URL = "https://facebook.com/Anime.Proxer.Me"
private const val TWITTER_URL = "https://twitter.com/proxerme"
private const val YOUTUBE_URL = "https://youtube.com/channel/UC7h-fT9Y9XFxuZ5GZpbcrtA"
private const val DISCORD_URL = "https://discord.gg/XwrEDmA"
private const val REPOSITORY_URL = "https://github.com/proxer/ProxerAndroid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }

    BackHandler(enabled = showLicenses) { showLicenses = false }

    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_info)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Info section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.app_name)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_version_title)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val title = context.getString(R.string.clipboard_title)
                        context.getSystemService<ClipboardManager>()?.setPrimaryClip(
                            ClipData.newPlainText(title, BuildConfig.VERSION_NAME),
                        )
                        context.toast(R.string.clipboard_status)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_licenses_title)) },
                    supportingContent = { Text(stringResource(R.string.about_licenses_description)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable { showLicenses = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_source_code)) },
                    supportingContent = { Text(stringResource(R.string.about_source_code_description)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_server_status)) },
                    supportingContent = { Text(stringResource(R.string.about_server_status_description)) },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    modifier = Modifier.clickable { ServerStatusActivity.navigateTo(context as Activity) },
                )
            }

            item { HorizontalDivider() }

            // --- Social Media section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_social_media_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_facebook_title)) },
                    supportingContent = { Text(stringResource(R.string.about_facebook_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(FACEBOOK_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_twitter_title)) },
                    supportingContent = { Text(stringResource(R.string.about_twitter_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(TWITTER_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_youtube_title)) },
                    supportingContent = { Text(stringResource(R.string.about_youtube_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_URL)))
                    },
                )
            }
            item {
                // Discord: no icon (CommunityMaterial.Icon.cmd_discord was removed in 7.x)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_discord_title)) },
                    supportingContent = { Text(stringResource(R.string.about_discord_description)) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL)))
                    },
                )
            }

            item { HorizontalDivider() }

            // --- Support section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_support_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_support_info)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(TEAM_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_support_mail_title)) },
                    supportingContent = { Text(stringResource(R.string.about_support_mail_description)) },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_PROXER_MAIL))
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_support_mail_subject))
                        }
                        context.startActivityOrToast(intent, R.string.about_error_mail_no_activity)
                    },
                )
            }

            item { HorizontalDivider() }

            // --- Developer section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_developer_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_developer_github_title)) },
                    supportingContent = { Text(DEVELOPER_GITHUB_NAME) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivityOrToast(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$DEVELOPER_GITHUB_NAME")),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    ProxerTheme {
        AboutScreen()
    }
}
```

- [ ] **Step 5: Confirm compilation passes**

Run: `./gradlew compileDebugKotlin`

Expected: PASS (no `:app:` prefix needed per this project's Gradle setup).

- [ ] **Step 6: Full debug build sanity check**

Run: `./gradlew assembleDebug`

Expected: PASS — confirms the AboutLibraries Gradle plugin generates `R.raw.aboutlibraries` correctly for the debug variant and the new dependencies resolve and package cleanly.

- [ ] **Step 7: Manual verification**

Run: `./gradlew installDebug` (requires a connected device/emulator), then in the app: open the drawer → Info → tap "Lizenzen" (Licenses). Confirm:
- The licenses list renders in place (no separate Activity/window opens).
- Tapping the back arrow in the licenses screen's top bar returns to the About list.
- Re-opening licenses and pressing the system back button also returns to the About list (not exiting the app).

- [ ] **Step 8: Commit**

```bash
git add gradle/repositories.gradle gradle/versions.gradle gradle/dependencies.gradle src/main/kotlin/me/proxer/app/settings/AboutScreen.kt
git commit -m "$(cat <<'EOF'
feat: migrate AboutLibraries to Compose (aboutlibraries-compose-m3 15.0.3)

Legacy com.mikepenz:aboutlibraries Activity API is discontinued past
14.2.1. Replace LibsBuilder().start() with an in-place LicensesScreen
composable, following the project's existing onBack + ArrowBack
navigation convention (no Compose Navigation library in use).

Also allow Maven Central to serve com.github.skydoves artifacts:
aboutlibraries-compose-m3's transitive compose-stability-runtime
dependency is published on Central (via Sonatype), not JitPack, and
was previously unresolvable because repositories.gradle routed all
com.github.* groups to JitPack only.
EOF
)"
```
