# Warning Cleanup Design — 2026-06-04

Fix all Kotlin compiler warnings and ktlint violations in ProxerAndroid without affecting functionality.

## Constraints

- `minSdk 23` — no version guards needed for any `androidx` compat API used here
- No new dependencies — all compat APIs (`IntentCompat`, `BundleCompat`, `WindowInsetsControllerCompat`, `MenuProvider`) are already on the classpath via existing `androidx.core` and `androidx.activity` dependencies
- Vendor files (`me.zhanghai`, `com.gojuno`, `androidx.recyclerview.widget.BindAwareViewHolder`) are excluded from ktlint, not reformatted

---

## Section 1 — Ktlint formatting (~5936 violations)

**Step 1:** Add exclusions to `.editorconfig`:

```editorconfig
[src/main/kotlin/me/zhanghai/**]
ktlint_disabled = true

[src/main/kotlin/com/gojuno/**]
ktlint_disabled = true

[src/main/kotlin/androidx/recyclerview/widget/BindAwareViewHolder.kt]
ktlint_disabled = true
```

**Step 2:** Run `./gradlew ktlintFormat` — auto-fixes all proxer-owned violations (trailing commas, blank lines, expression bodies, etc.).

**Step 3:** Verify: `./gradlew ktlintCheck` must pass.

---

## Section 2 — Kotlin stdlib + Koin DSL deprecations

| Deprecated | Replacement | File(s) |
|---|---|---|
| `sumBy { }` | `sumOf { it.toInt() }` | any file using it |
| `org.koin.androidx.viewmodel.dsl.viewModel` import | `org.koin.core.module.dsl.viewModel` | `MainModules.kt` |
| `androidx.core.os.bundleOf` (if flagged) | `androidx.core.bundle.bundleOf` | any file using it |

Koin import change alone eliminates ~50 compiler warnings in `MainModules.kt`.

---

## Section 3 — Parcelable / Serializable compat

Use `androidx.core.content.IntentCompat` and `androidx.core.os.BundleCompat`. These APIs exist on all SDK versions ≥ minSdk 23.

| Old API | New API |
|---|---|
| `intent.getParcelableExtra<T>(key)` | `IntentCompat.getParcelableExtra(intent, key, T::class.java)` |
| `intent.getParcelableArrayListExtra<T>(key)` | `IntentCompat.getParcelableArrayListExtra(intent, key, T::class.java)` |
| `intent.getSerializableExtra(key)` | `IntentCompat.getSerializableExtra(intent, key, T::class.java)` |
| `bundle.getParcelable<T>(key)` | `BundleCompat.getParcelable(bundle, key, T::class.java)` |
| `bundle.getParcelableArrayList<T>(key)` | `BundleCompat.getParcelableArrayList(bundle, key, T::class.java)` |
| `bundle.getSerializable(key)` | `BundleCompat.getSerializable(bundle, key, T::class.java)` |

Affected files: `MainActivity`, `LocalUser`, `AnimeActivity`, `AppRequiredDialog`, `NoWifiDialog`, `MangaActivity`, `MediaActivity`, `MessengerDatabase`, `MessengerNotifications`, `LocalProfileSettings`, `LocalDataInitializer`, `AndroidExtensions`, `NullabilityExtensions`, and others.

---

## Section 4 — Window / system bar APIs → `WindowInsetsControllerCompat`

Replace all `systemUiVisibility`, `statusBarColor`, `FLAG_TRANSLUCENT_*` usage.

### Mapping

| Old | New |
|---|---|
| `window.addFlags(FLAG_TRANSLUCENT_STATUS \| FLAG_TRANSLUCENT_NAVIGATION)` | `WindowCompat.setDecorFitsSystemWindows(window, false)` |
| `view.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE \| ...` | `WindowCompat.setDecorFitsSystemWindows(window, false)` |
| `view.systemUiVisibility = SYSTEM_UI_FLAG_FULLSCREEN \| SYSTEM_UI_FLAG_HIDE_NAVIGATION \| ...` | `WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.systemBars())` |
| `view.systemUiVisibility = SYSTEM_UI_FLAG_VISIBLE` | `WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())` |
| `SYSTEM_UI_FLAG_IMMERSIVE` behavior | `.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` |
| `view.setOnSystemUiVisibilityChangeListener { }` | `ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets -> ... }` |
| `window.statusBarColor = color` | No direct compat equivalent — use `WindowInsetsControllerCompat.isAppearanceLightStatusBars` for light/dark appearance; color tinting via theme |

Affected files: `BaseActivity`, `DrawerActivity`, `StreamActivity`, `TouchablePlayerView`, `DeviceUtils`, `Theme`, `ThemeVariant`, `TwoColorSelectableDrawable`.

---

## Section 5 — Fragment options menu → `MenuProvider`

Replace the deprecated fragment menu lifecycle hooks with the `MenuProvider` API from `androidx.activity`.

### Old pattern

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
}

override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.fragment_foo, menu)
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) { ... }
}
```

### New pattern

```kotlin
// in onViewCreated:
requireActivity().addMenuProvider(object : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_foo, menu)
    }
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) { ... }
    }
}, viewLifecycleOwner)
```

Remove `setHasOptionsMenu()` call and the three deprecated overrides. Lifecycle is automatically tied to `viewLifecycleOwner`.

Affected files: ~20 fragments including `AnimeFragment`, `BookmarkFragment`, `ConferenceFragment`, `MessengerFragment`, `ChatFragment`, `CommentsFragment`, `MediaListFragment`, `NotificationFragment`, `ProfileCommentFragment`, `AboutFragment`, etc.

---

## Section 6 — Display metrics

| Old | New |
|---|---|
| `window.defaultDisplay` | `context.resources.displayMetrics` (for pixel density) |
| `display.getMetrics(dm)` | `context.resources.displayMetrics` |
| `displayMetrics.scaledDensity` | `TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)` gives 1sp in px |
| `windowManager.defaultDisplay.getRealMetrics(dm)` | `WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds` |

Affected files: `DeviceUtils`, `TwoColorSelectableDrawable`, `BBCodeView`, `BBSpoilerView`, `KotterKnifePreference`.

---

## Section 7 — Miscellaneous compat fixes

| Deprecated | Replacement | File(s) |
|---|---|---|
| `ShareCompat.IntentBuilder.from(activity)` | `ShareCompat.IntentBuilder(context)` | `LinkCheckDialog` |
| `ViewCompat.animate(view)` | `view.animate()` | various |
| `ViewCompat.isAttachedToWindow(view)` | `view.isAttachedToWindow` | various |
| `MasterKey.Builder(context)` | `MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)` | `MainModules.kt` |
| `C.TYPE_HLS / TYPE_DASH / TYPE_SS / TYPE_OTHER` | `C.CONTENT_TYPE_HLS / CONTENT_TYPE_DASH / CONTENT_TYPE_SS / CONTENT_TYPE_UNKNOWN` | `StreamPlayerManager` |
| `InputMethodManager.SHOW_IMPLICIT` | `InputMethodManager.SHOW_FORCED` or `0` | `AndroidExtensions` |

---

## Section 8 — `@Suppress("DEPRECATION")` for no-compat paths

These APIs have no drop-in compat replacement without significant refactoring beyond the scope of a warning-cleanup PR. Add targeted `@Suppress("DEPRECATION")` at the call site.

| API | Reason |
|---|---|
| `CastPlayer(CastContext)` | Cast SDK internal deprecation; replacement is Builder-based but changes construction flow |
| `CastPlayer.setSessionAvailabilityListener()` | Same SDK; deprecated but no compat wrapper |
| `AppUpdateManager.startUpdateFlowForResult(AppUpdateInfo, Int, Activity, Int)` | New API requires Activity Result Launcher refactor — significant behavior change |
| `MaterialDialog.neutralButton()` | Library design warning, not removing neutral buttons changes UI |
| `LibsBuilder` | AboutLibraries legacy UI — replacing whole component is out of scope |
| `EncryptedSharedPreferences` (if whole-class deprecated) | Investigate: if only the old factory method is deprecated, update call site; if whole class, suppress |

---

## Verification

After all sections:

1. `./gradlew ktlintCheck` — zero violations
2. `./gradlew compileDebugKotlin --rerun-tasks 2>&1 | grep "^w:"` — zero lines
3. `./gradlew assembleDebug` — build succeeds

No functionality tests exist in the project. Compile-clean = done.
