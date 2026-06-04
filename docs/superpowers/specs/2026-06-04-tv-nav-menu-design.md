# TV Navigation Menu Design

**Date:** 2026-06-04  
**Scope:** ProxerAndroid TV frontend (`me.proxer.app.tv`)

## Summary

Replace the current ad-hoc top-bar buttons in `TvBrowseScreen` with a proper side navigation menu. The menu uses the `NavigationDrawer` component from `androidx.tv:tv-material:1.0.0` — a collapsible left rail that shows icons when collapsed and full labels when expanded via D-pad focus.

---

## Architecture

### Single-Activity Shell

`TvMainActivity` becomes a shell that hosts a `TvAppShell` composable. All main sections are composable screens swapped inside the shell — no new Activity per section. Detail/modal flows (`TvLoginActivity`, `TvSearchActivity`, `TvMediaDetailActivity`, `TvEpisodeActivity`) remain separate Activities.

```
TvMainActivity
  └─ MaterialTheme
       └─ TvAppShell
            ├─ NavigationDrawer (androidx.tv.material3)
            │    └─ TvNavigationDrawer  ← drawer content
            └─ content slot (switches by TvSection)
                 ├─ TvBrowseScreen      ← Anime (existing)
                 ├─ TvPlaceholderScreen ← News, Bookmarks, Schedule, Info, Settings
```

### TvSection Enum

```kotlin
enum class TvSection { ANIME, NEWS, BOOKMARKS, SCHEDULE, INFO, SETTINGS }
```

Active section stored as `remember { mutableStateOf(TvSection.ANIME) }` in `TvAppShell`. No ViewModel needed for routing — it's pure local UI state.

---

## Components

### TvAppShell (`tv/TvAppShell.kt`)

- Obtains `TvShellViewModel` via `koinViewModel()`
- Observes `viewModel.user` to pass auth state down to the drawer
- Holds `currentSection` state; passes `onSectionSelected` to drawer
- Wraps content in `NavigationDrawer`, renders the correct screen in the content lambda
- On Sign In click: `context.startActivity<TvLoginActivity>()`
- On Sign Out click: `viewModel.logout()`

### TvNavigationDrawer (`tv/TvNavigationDrawer.kt`)

Stateless composable receiving:
```kotlin
fun TvNavigationDrawer(
    currentSection: TvSection,
    user: LocalUser?,
    onSectionSelected: (TvSection) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
)
```

**Profile header (top):**
- Logged in: circular avatar (initials fallback; Glide/Coil image if `user.image` non-blank) + username + red "Sign Out" focusable text
- Logged out: grey person icon + "Guest" label + blue "Sign In →" focusable text

**Nav items (middle):**

| Item | Icon | Section |
|---|---|---|
| Anime | `cmd_television` (CommunityMaterial) | `ANIME` |
| News | `cmd_newspaper` | `NEWS` |
| Bookmarks | `cmd_bookmark` | `BOOKMARKS` |
| Schedule | `cmd_calendar` | `SCHEDULE` |

**Footer items (bottom, separated by divider):**

| Item | Icon | Section |
|---|---|---|
| Info | `cmd_information_outline` | `INFO` |
| Settings | `cmd_cog` | `SETTINGS` |

Active item highlighted with `NavigationDrawerItem(selected = currentSection == section)`.

### TvShellViewModel (`tv/TvShellViewModel.kt`)

```kotlin
class TvShellViewModel : ViewModel() {
    private val storageHelper by safeInject<StorageHelper>()
    private val api by safeInject<ProxerApi>()

    val user: LiveData<LocalUser?> =
        storageHelper.isLoggedInObservable.map { storageHelper.user }.toLiveData()

    val logoutError = MutableLiveData<ErrorAction?>()
    val isLoggingOut = MutableLiveData<Boolean?>()

    fun logout() { /* mirrors LogoutViewModel: api.user.logout().buildSingle()... */ }
}
```

- `user` derived from `storageHelper.isLoggedInObservable.map { storageHelper.user }` converted to `LiveData` via RxJava2 `toLiveData()` — emits `null` when logged out, `LocalUser` when logged in
- `logout()` replicates `LogoutViewModel` logic inline: calls `api.user.logout()`, handles errors via `logoutError` LiveData; `LogoutViewModel` is not reused directly because it is a ViewModel (not a Koin singleton) and cannot be safeInject'd

### TvPlaceholderScreen (`tv/TvPlaceholderScreen.kt`)

Simple centered composable:
```kotlin
@Composable
fun TvPlaceholderScreen(sectionName: String)
```
Shows section name + "Coming soon" message. Used for News, Bookmarks, Schedule, Info, Settings until those screens are implemented.

---

## Modified Files

### TvMainActivity.kt

Replace direct `TvBrowseScreen(...)` call with `TvAppShell()`. Remove `onLoginClick` wiring (login now handled inside the shell).

### TvBrowseScreen.kt

Remove `onLoginClick: () -> Unit` parameter (no longer needed — login entry point moved to drawer). Update call sites.

---

## Auth Flow

**Sign In:** "Sign In →" in drawer header → `startActivity<TvLoginActivity>()`. `TvLoginActivity` calls `finish()` on success. Auth state propagates back via `storageHelper.isLoggedInObservable` → `TvShellViewModel.user` LiveData → drawer re-renders.

**Sign Out:** "Sign Out" in drawer header → `TvShellViewModel.logout()`. On success, `storageHelper` clears the user; `isLoggedInObservable` fires; `user` LiveData emits `null`; drawer switches to logged-out state.

No confirmation dialog on logout (consistent with mobile app behaviour).

---

## Drawer Behaviour (Compose for TV NavigationDrawer)

- Collapsed: icon-only rail (~56dp wide), avatar shown as small circle
- Expanded: full drawer (~200dp wide) with labels, triggered when D-pad focus moves left to the rail
- Content area dims slightly when drawer is expanded (standard `NavigationDrawer` overlay behaviour)
- Selecting a nav item closes the drawer and updates `currentSection`

---

## Out of Scope

- Implementing actual News, Bookmarks, Schedule, Info, Settings screens — placeholders only
- User profile page navigation from drawer (not in phone TV equivalent yet)
- Manga section (explicitly excluded by user)
- Chat/Messenger section (not useful on TV)
