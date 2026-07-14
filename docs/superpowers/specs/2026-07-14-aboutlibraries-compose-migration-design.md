# AboutLibraries Compose Migration

## Context

`AboutScreen.kt` (already Compose/Material3) opens the licenses list via the
legacy `com.mikepenz:aboutlibraries` Activity API:

```kotlin
@Suppress("DEPRECATION")
LibsBuilder()
    .withActivityTitle(context.getString(R.string.about_licenses_activity_title))
    .start(context as Activity)
```

The library is pinned at `14.2.1` (`gradle/versions.gradle:64`). This Activity
API is deprecated upstream in favor of a Compose-native `LibrariesContainer`
composable, shipped in `aboutlibraries-compose-m3` (matches this project's
Material3 usage). Latest upstream release: `15.0.3`.

The project has no Compose Navigation / back-stack library anywhere.
Navigation is a manual `when(selectedItem)` state switch (`MainScreen.kt:311`),
and sub-screens elsewhere use an `onBack: () -> Unit` lambda param with a
`TopAppBar` `navigationIcon` showing `Icons.AutoMirrored.Filled.ArrowBack`
(e.g. `AnimeScreen.kt:390-406`). This migration follows that same convention
rather than introducing a new Activity or a nav library.

## Scope

Bump the AboutLibraries dependency and plugin to `15.0.3`, and replace the
Activity-launch licenses flow with an in-place composable state toggle inside
`AboutScreen.kt`.

## Components

1. **`gradle/versions.gradle:64`** — bump `aboutLibrariesVersion` to `"15.0.3"`.
2. **`gradle/dependencies.gradle:53`** — replace:
   ```groovy
   implementation "com.mikepenz:aboutlibraries:$aboutLibrariesVersion"
   ```
   with:
   ```groovy
   implementation "com.mikepenz:aboutlibraries-compose-m3:$aboutLibrariesVersion"
   ```
3. **`AboutScreen.kt`**:
   - Remove the `LibsBuilder` import and its usage; drop the
     `@Suppress("DEPRECATION")`.
   - Add `var showLicenses by remember { mutableStateOf(false) }` in
     `AboutScreen`.
   - Add `BackHandler(enabled = showLicenses) { showLicenses = false }`
     (from `androidx.activity.compose`, already on the classpath via existing
     Compose usage) so the system back button returns to the About list
     instead of exiting the screen.
   - The Licenses `ListItem.clickable` sets `showLicenses = true` instead of
     invoking `LibsBuilder`.
   - When `showLicenses` is true, render a small licenses sub-screen in place
     of the main list: a `Scaffold` with a `TopAppBar` (title = licenses
     string resource already used for the activity title,
     `navigationIcon` = `IconButton(onClick = onBack)` showing
     `Icons.AutoMirrored.Filled.ArrowBack`, matching the project convention),
     and body = `LibrariesContainer(modifier = Modifier.fillMaxSize().padding(padding))`.
   - This sub-screen composable can live inline in `AboutScreen.kt` (small,
     single caller) rather than as a new file.

No changes are needed to the AboutLibraries Gradle plugin configuration —
none exists today (defaults only). The plugin continues to generate the
license metadata at build time; only the runtime consumption API changes
from Activity-launch to `LibrariesContainer`/`rememberLibraries()`.

## Data Flow

Unchanged at the plugin/build level: the Gradle plugin still scans
dependencies at build time and writes the metadata file. Only the
*consumption* side changes — from starting a separate Activity to rendering
a composable that reads the same generated metadata in-process.

## Error Handling

None needed. `LibrariesContainer` handles empty/missing metadata internally.
No network or IO calls are introduced by this change.

## Testing

UI-only change; no ViewModel or business logic involved, so no new unit
tests apply. Verify via:
- `./gradlew assembleDebug` (compiles, dependency resolves)
- Manual check in-app: About → Licenses opens in place showing the license
  list, back button (both the in-screen arrow and system back) returns to
  the About list without exiting the screen or app.
