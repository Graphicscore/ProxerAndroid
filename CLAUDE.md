# ProxerAndroid

Android client for [Proxer.me](https://proxer.me). **Not maintained** — no more updates planned.

## Build

Requires `secrets.properties` in project root (gitignored) with at least `PROXER_API_KEY=<key>`.
**Never overwrite `secrets.properties` if it already exists** — it holds real keys/keystore config. If missing, for build-testing only: `echo "PROXER_API_KEY=dummy_build_key" > secrets.properties`.

**Always use `./gradlew`, never system `gradle`** (7.6.3, incompatible with Java 21 JBR). **Always run with the daemon on** (default — don't pass `--no-daemon`) to keep builds fast.

```bash
./gradlew assembleDebug       # unobfuscated, logging on
./gradlew assembleRelease     # obfuscated + signed (needs keystore in secrets.properties)
./gradlew installDebug        # build + install to connected device
./gradlew testDebugUnitTest   # 345+ JVM unit tests (src/test/kotlin)
./gradlew compileDebugKotlin  # fast type-check, no :app: prefix
./gradlew detekt              # static analysis (permissive config)
./gradlew lint                # Android lint
```

Gradle JBR: `/opt/android-studio/jbr` (set via `org.gradle.java.home`). Filter tests with `--tests "..."` (the plain `test` task doesn't accept it here). Never run `./gradlew test*` concurrently on the same checkout — corrupts `build/test-results`; fix with `rm -rf build`.

## Architecture

MVVM, RxJava 2, no coroutines. Package layout under `me.proxer.app/`: `base/` (Activity/Fragment/ViewModel base classes), `anime/` (Media3 player), `auth/`, `chat/` (prv/pub), `manga/` (SubsamplingScaleImageView reader), `media/`, `news/`, `profile/`, `settings/`, `ui/` (shared views, BBCode renderer), `util/` (ErrorUtils, HTTP interceptors).

- `BaseViewModel` — RxJava `Single<T>` → `LiveData`. `isLoginRequired = true` by default (only `ServerStatusViewModel` opts out). **No auto-load** — `BaseContentFragment.onViewCreated` calls `viewModel.load()`; Compose screens need `LaunchedEffect(Unit) { viewModel.load() }` manually. `isLoggedInObservable` uses `.skip(1)`, so already-logged-in users at cold start rely on the explicit `load()` call, not the reactive path.
- DI: Koin 4.2.1, modules in `MainModules.kt`, inject via `safeInject<Type>()`. In composables use `koinInject<T>()` (`get()`/`sharedViewModel` don't exist in 4.x).
- Always wrap subscriptions with AutoDispose: `.autoDisposable(scope()).subscribe(...)` — missing it leaks.
- Persistence: Room (`MessengerDatabase`/chat.db, `TagDatabase`/tag.db, migrate on schema change), `StorageHelper` (EncryptedSharedPreferences, login tokens), `PreferenceHelper` (non-sensitive settings).
- Error handling: `ErrorUtils.handle(Throwable)` → `ErrorAction`, surfaced via `BaseViewModel.error`. Age-restriction errors: `AgeConfirmationRequiredException` → set `preferenceHelper.isAgeRestrictedMediaAllowed = true`, VM auto-reloads.

TV frontend lives on the `tv-support` branch (`me.proxer.app.tv`), all Compose (Compose for TV + Material3), no Fragments.

## Unit Testing

Shared infra in `me.proxer.app.base`: `RxTrampolineRule`, `ProxerEndpointTestUtils.kt`, `FakeAppModule.kt`.

- Need ProxerLibJava internals (endpoint/entity source, not just the jar)? Source checked out at `../ProxerLibJava` — read it there instead of decompiling/extracting the jar.
- ProxerLibJava endpoints often return concrete subtypes (e.g. `EntryCoreEndpoint`) — mock the concrete type, not `Endpoint<T>`.
- Nullable endpoints (`Endpoint<T?>`) run through `ProxerCallNullableSingle.execute()` — use `stubNullableSuccess`/`stubNullableError`, not the non-null variants.
- Entities with BBCode (`LocalComment`, `LocalMessage`, `ParsedPost`) call `.toSimpleBBTree()` on construction and crash on unmocked `SpannableStringBuilder` — stub `mockkObject(TextPrototype)` in `@Before`/`unmockkObject` in `@After`.
- `ErrorUtils` binds `StorageHelper`/`PreferenceHelper` via `by lazy` once per JVM — first test touching it poisons the rest of the suite. `build.gradle` sets `forkEvery = 4` to bound this.
- JVM tests hitting `ZoneId.systemDefault()` need `testImplementation "org.threeten:threetenbp:1.7.1"` (plain jar bundles `tzdb.dat`; `threetenabp`'s no-tzdb classifier expects Android-only init).

## Toolchain

| Property | Value |
|---|---|
| AGP | 9.2.1 |
| Kotlin | 2.2.10 (AGP built-in, no external Kotlin plugin) |
| Java | 17 (JBR) — don't add to `gradle.properties` |
| Gradle | 9.5.1 |
| NDK | r29 |
| KSP | 2.2.10-2.0.2 |
| Glide | 5.0.7 (KSP, no generated API — use `Glide.with()`/`RequestManager`) |
| minSdk / target / compile | 23 / 37 / 37 |

## Key Gotchas

- `secrets.properties` is gitignored and required — build fails without it. See `gradle/utils.gradle` for required keys. **Do not overwrite an existing one.**
- `android.nonTransitiveRClass=false` — needed for unqualified `R.attr.colorPrimary` etc. from Material Components.
- `android.disallowKotlinSourceSets=false` — required for KSP-generated sources under AGP's built-in Kotlin.
- `BUILD_CONFIG=true` set explicitly (AGP 8+ disables by default).
- `coreLibraryDesugaringEnabled true` + `desugar_jdk_libs` required by IMA 3.37+.
- `--enable-native-access=ALL-UNNAMED` must be set in both `org.gradle.jvmargs` and `gradlew`'s `DEFAULT_JVM_OPTS` — daemon and wrapper are separate JVM processes.
- `applicationVariants.all {}` removed in AGP 9 — use `androidComponents.onVariants {}` for custom APK naming.
- `lifecycle`/`core-ktx` ceiling: 2.10.0 / 1.18.0 (newer needs compileSdk 37 stable).
- `concealVersion` deleted — Hawk/Conceal removed for 16KB page size compat.
- `CommunityMaterial.Icon.cmd_discord` removed in 7.x — Discord entry in `AboutFragment.kt` has no icon currently.
- `gh` CLI not installed — use `git push` + open PRs manually at `https://github.com/Graphicscore/ProxerAndroid/compare/<base>...<branch>`.
- Source root is `src/` (no `app/` subdir). Exclude `.claude/worktrees/` from `find`.
- `androidx.tv.material3.NavigationDrawerItem` is a `NavigationDrawerScope` extension — callers must themselves be declared `fun NavigationDrawerScope.Foo(...)`.
- Standalone ViewModels (not extending `BaseViewModel`) must seed `MutableLiveData` with `storageHelper.user` in the constructor, then subscribe (`isLoggedInObservable` skips the current value).
- `.superpowers/` dir from brainstorming tool — keep in `.gitignore`.
