# ProxerAndroid

Android client for [Proxer.me](https://proxer.me). **Not maintained** — README states the app no longer receives updates.

## Build

Requires `secrets.properties` in the project root with at least:
```
PROXER_API_KEY=<your_key>
```
For build-testing without a real key: `echo "PROXER_API_KEY=dummy_build_key" > secrets.properties`

**Always use `./gradlew`, not `gradle`** — the system `gradle` binary is 7.6.3 (incompatible with Java 21 JBR).

Gradle uses the JBR at `/opt/android-studio/jbr` (configured via `org.gradle.java.home` in `gradle.properties`).

```bash
./gradlew assembleDebug --no-daemon --max-workers 2   # reliable in low-resource / CI contexts
./gradlew assembleRelease     # obfuscated + signed (needs keystore in secrets.properties)
./gradlew installDebug        # build + install to connected device
./gradlew ktlint              # style check
./gradlew detekt              # static analysis
./gradlew lint                # Android lint
```

No unit or instrumented tests exist in the project.

Three build variants: **debug** (unobfuscated, logging on), **release** (obfuscated, no logging), **logRelease** (obfuscated, logging on).

## Android TV Frontend

TV implementation lives on the `tv-support` branch in `me.proxer.app.tv`. All screens are Compose (Compose for TV + Material3); activities are plain `ComponentActivity`. No Fragments.

- `TvMainActivity` → `TvBrowseScreen` (browse/home)
- `TvLoginActivity` → `TvLoginScreen` (mirrors `LoginDialog` flow including 2FA)
- `TvSearchActivity` → `TvSearchScreen`
- `TvMediaDetailActivity` → `TvMediaDetailScreen`
- `TvEpisodeActivity` → `TvEpisodeScreen`
- Shared error composable: `TvErrorView` (`me.proxer.app.tv`)

## Architecture

MVVM with RxJava 2. No coroutines.

```
me.proxer.app/
├── base/          Base Activity / Fragment / ViewModel classes
├── anime/         Streaming player (Media3), schedule widget
├── auth/          Login, logout, token management
├── chat/          Private (prv/) and public (pub/) chat
├── manga/         Manga reader (SubsamplingScaleImageView)
├── media/         Media list, episodes, comments, recommendations
├── news/          News feed + widget
├── profile/       User profile, history, top-ten, settings
├── settings/      App preferences, server status
├── ui/            Shared views and BBCode renderer
└── util/          ErrorUtils, data helpers, HTTP interceptors
```

Key base classes:
- `BaseViewModel` — wraps RxJava `Single<T>` into `LiveData`; handles loading, error, and reload states
  - **`isLoginRequired = true` by default** — all VMs require login unless overridden. Only `ServerStatusViewModel` sets it `false`.
  - **No auto-load** — `BaseContentFragment.onViewCreated` calls `viewModel.load()`. Compose screens must do this manually: `LaunchedEffect(Unit) { viewModel.load() }`.
  - `isLoggedInObservable` uses `.skip(1)` — already-logged-in users at cold start do not trigger the reactive reload; the explicit `load()` call is the only trigger.
- `BaseContentFragment<T>` — subscribes to ViewModel, renders swipe-to-refresh + error UI
- `BaseActivity` — applies theme dynamically, provides snackbar helper

## Dependency Injection

Koin 3.5.6. All modules in `MainModules.kt`. Singletons created at app startup (OkHttpClient, ProxerApi, Moshi, Room databases, PreferenceHelper, StorageHelper). ViewModels registered with `viewModel { }`.

Inject via `safeInject<Type>()` (delegates to Koin's `inject()`).

## Async / RxJava Patterns

- All async work: RxJava 2 (no coroutines)
- IO on `Schedulers.io()`, results on `AndroidSchedulers.mainThread()`
- **Always use AutoDispose** when subscribing in Activity/Fragment — `.autoDisposable(scope()).subscribe(...)`. Missing it causes leaks.
- `RxBus` for cross-component events (captcha trigger, network recovery)

## Persistence

- **Room**: `MessengerDatabase` (`chat.db`), `TagDatabase` (`tag.db`). Schema in `schemas/`. Add migrations for schema changes.
- **EncryptedSharedPreferences**: sensitive data (login tokens) via `StorageHelper`. Master key in AndroidKeyStore.
- **PreferenceManager**: non-sensitive settings via `PreferenceHelper`.

## Networking

OkHttp 4.12.0 + Retrofit 2.12.0 via `ProxerLibJava:5.4.0`. Custom interceptors in `util/http/`:

| Interceptor | Role |
|---|---|
| `CacheInterceptor` | Controls caching headers |
| `ConnectivityInterceptor` | Fails fast if offline |
| `HttpsUpgradeInterceptor` | Upgrades HTTP → HTTPS |
| `UserAgentInterceptor` | Appends custom User-Agent |
| `ConnectionCloseInterceptor` | Closes stale connections |
| `BrotliInterceptor` | Brotli decompression |

10 MB HTTP cache at `${cacheDir}/http`.

## Media Playback

Anime streaming: Media3 1.10.1 (`StreamActivity`, `StreamPlayerManager`, `TouchablePlayerView`). Supports HLS, DASH, SmoothStreaming, progressive. IMA ad integration. Chromecast via Cast framework.

Manga reader: `SubsamplingScaleImageView` with Android's built-in `BitmapFactory` / `BitmapRegionDecoder`. PDF rendering: `AndroidPdfDecoder` / `AndroidPdfRegionDecoder` using `android.graphics.pdf.PdfRenderer` (API 23+).

## Error Handling

`ErrorUtils.handle(Throwable)` → `ErrorAction` (user-facing string + optional button). Propagated via `BaseViewModel.error: LiveData<ErrorAction>`. `BaseContentFragment` renders the error UI automatically.

`ErrorAction` button sentinel constants (`ACTION_MESSAGE_DEFAULT = -1`, `ACTION_MESSAGE_HIDE = -2`) are in the companion object — import as `ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT`.

Age-confirmation flow: `AgeConfirmationRequiredException` → `ButtonAction.AGE_CONFIRMATION`. Confirm by setting `preferenceHelper.isAgeRestrictedMediaAllowed = true`; the ViewModel reloads automatically via `isAgeRestrictedMediaAllowedObservable`. Mobile uses `AgeConfirmationDialog`; TV uses a Compose `AlertDialog` in `TvErrorView`.

## Toolchain

| Property | Value                                                                                     |
|---|-------------------------------------------------------------------------------------------|
| AGP | 9.2.1                                                                                     |
| Kotlin | 2.4.0                                                                                     |
| Java | 17 (JBR at `/opt/android-studio/jbr`) do not add it to the gradle.properties files        |
| Gradle | 9.5.1                                                                                     |
| NDK | r29 (29.0.14206865)                                                                       |
| KSP | 2.3.9 (Room, Moshi, Glide — uses `com.github.bumptech.glide:ksp` artifact)               |
| Glide | 5.0.7 (KSP; no generated API — use `Glide.with()`, `RequestManager`, `RequestBuilder`) |
| minSdk | 23                                                                                        |
| targetSdk / compileSdk | 36                                                                                        |

## Key Gotchas

- `android.nonTransitiveRClass=false` is set in `gradle.properties` — required because the codebase uses `R.attr.colorPrimary` etc. from Material Components without fully-qualified class names.
- `secrets.properties` is gitignored. Without it, build fails. See `gradle/utils.gradle` for required keys.
- `BUILD_CONFIG=true` is explicit in `build.gradle` — AGP 8.0+ disables it by default.
- Detekt and ktlint are configured permissively; most checks disabled. Don't rely on them to catch correctness issues.
- `gh` CLI is not installed on this machine — use `git push` + open PRs at `https://github.com/Graphicscore/ProxerAndroid/compare/<base>...<branch>`.
- `android.builtInKotlin=false` + `android.newDsl=false` in `gradle.properties` — both needed to use `org.jetbrains.kotlin.android` plugin (Kotlin 2.4.0) with AGP 9. Without them AGP enforces its bundled Kotlin (2.2.0) and rejects the external plugin. Remove when AGP bundles a compatible Kotlin version.
- `coreLibraryDesugaringEnabled true` + `desugar_jdk_libs` dependency required by IMA (interactive media ads) 3.37+.
- `CommunityMaterial.Icon.cmd_discord` was removed in CommunityMaterial 7.x — Discord entry in `AboutFragment.kt` currently has no icon. Find an equivalent in CommunityMaterial 7.x or add a custom typeface before re-adding it.
- `applicationVariants.all {}` was removed in AGP 9 — APK output now uses AGP defaults instead of `app-1.11.5.apk`. Re-implement with `androidComponents.onVariants {}` if custom naming is needed.
- `lifecycle` 2.11 and `core-ktx` 1.19 require compileSdk 37 (not yet stable). Current ceiling: lifecycle 2.10.0, core-ktx 1.18.0.
- `concealVersion` was deleted — Hawk/Conceal removed for 16KB page size compatibility.
- `koin-androidx-compose` 3.5.6 exposes `get<T>()` for singleton injection in composables, **not** `koinInject()`. Import: `org.koin.androidx.compose.get`.
- `./gradlew compileDebugKotlin` (no `:app:` prefix) — fast type-check without a full build.
- Source root is `src/` at the project root (no `app/` subdirectory). `.claude/worktrees/` dirs appear in `find` results — exclude with `-not -path "*/.claude/*"`.
