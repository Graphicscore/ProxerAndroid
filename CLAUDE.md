# ProxerAndroid

Android client for [Proxer.me](https://proxer.me). **Not maintained** — README states the app no longer receives updates.

## Build

Requires `secrets.properties` in the project root with at least:
```
PROXER_API_KEY=<your_key>
```

Gradle uses the JBR at `/opt/android-studio/jbr` (configured via `org.gradle.java.home` in `gradle.properties`).

```bash
./gradlew assembleDebug       # debug build
./gradlew assembleRelease     # obfuscated + signed (needs keystore in secrets.properties)
./gradlew installDebug        # build + install to connected device
./gradlew ktlint              # style check
./gradlew detekt              # static analysis
./gradlew lint                # Android lint
```

No unit or instrumented tests exist in the project.

Three build variants: **debug** (unobfuscated, logging on), **release** (obfuscated, no logging), **logRelease** (obfuscated, logging on).

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
- `BaseContentFragment<T>` — subscribes to ViewModel, renders swipe-to-refresh + error UI
- `BaseActivity` — applies theme dynamically, provides snackbar helper

## Dependency Injection

Koin 3.4.3. All modules in `MainModules.kt`. Singletons created at app startup (OkHttpClient, ProxerApi, Moshi, Room databases, PreferenceHelper, StorageHelper). ViewModels registered with `viewModel { }`.

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

OkHttp 4.10.0 + Retrofit via `ProxerLibJava:5.4.0`. Custom interceptors in `util/http/`:

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

Anime streaming: Media3 1.4.1 (`StreamActivity`, `StreamPlayerManager`, `TouchablePlayerView`). Supports HLS, DASH, SmoothStreaming, progressive. IMA ad integration. Chromecast via Cast framework.

Manga reader: `SubsamplingScaleImageView` with Android's built-in `BitmapFactory` / `BitmapRegionDecoder`. PDF rendering: `AndroidPdfDecoder` / `AndroidPdfRegionDecoder` using `android.graphics.pdf.PdfRenderer` (API 21+).

## Error Handling

`ErrorUtils.handle(Throwable)` → `ErrorAction` (user-facing string + optional button). Propagated via `BaseViewModel.error: LiveData<ErrorAction>`. `BaseContentFragment` renders the error UI automatically.

## Toolchain

| Property | Value |
|---|---|
| AGP | 8.7.2 |
| Kotlin | 1.9.25 |
| Java | 17 (JBR at `/opt/android-studio/jbr`) |
| Gradle | 8.10.2 |
| NDK | r27b (27.2.12479018) — 16KB ELF page alignment |
| minSdk | 21 |
| targetSdk / compileSdk | 35 |

## Key Gotchas

- `android.nonTransitiveRClass=false` is set in `gradle.properties` — required because the codebase uses `R.attr.colorPrimary` etc. from Material Components without fully-qualified class names.
- `secrets.properties` is gitignored. Without it, build fails. See `gradle/utils.gradle` for required keys.
- `BUILD_CONFIG=true` is explicit in `build.gradle` — AGP 8.0+ disables it by default.
- Detekt and ktlint are configured permissively; most checks disabled. Don't rely on them to catch correctness issues.
- `concealVersion` remains in `versions.gradle` for historical reference but is unused — Hawk/Conceal was removed for 16KB page size compatibility.
