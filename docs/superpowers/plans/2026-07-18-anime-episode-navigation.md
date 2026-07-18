# Anime Episode Navigation and Autoplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the anime player next/previous episode controls and automatic continuation into the next episode when the current one ends.

**Architecture:** `StreamActivity` receives an already-resolved video URL plus (new) `episodeAmount` and `hosterName` intent extras, so it can render its controls with no network call. A new standalone `StreamEpisodeViewModel` resolves a neighbouring episode on demand — listing hosters for that episode, preferring the one already in use, and walking candidates until one resolves to a playable `Video`. The activity then swaps the media source in place rather than restarting itself.

**Tech Stack:** Kotlin, Jetpack Compose, Media3/ExoPlayer, RxJava 2 (no coroutines in this codebase's ViewModels), Koin 4.2.1, JUnit 4 + MockK.

**Spec:** `docs/superpowers/specs/2026-07-18-anime-episode-navigation-design.md`

---

## Context an implementer needs before starting

Read these before Task 1. They are short and they explain non-obvious constraints.

**How a stream reaches the player today.** `AnimeScreen` calls `viewModel.resolve(stream)`. On success `AnimeViewModel.resolutionResult` emits a `StreamResolutionResult`. `AnimeScreen`'s `ObserveLiveDataEvent` block calls `result.play(...)` for the `Video` case, which builds an `Intent` and starts `StreamActivity`. The player therefore never sees the `AnimeStream` object — only the finished URL and a handful of extras.

**Resolution is two-step.** `api.anime.streams(entryId, episode, language)` returns `List<Stream>` (one entry per hoster upload). Turning one into something playable needs `StreamResolverFactory.resolverFor(stream.hosterName)?.resolve(stream.id)`, which returns `Single<StreamResolutionResult>`. That result may be `Video`, `Link`, `App` or `Message` — only `Video` can play in the internal player.

**JVM unit tests cannot touch `android.content.Intent`.** This project sets neither `testOptions.unitTests.returnDefaultValues` nor Robolectric, so any `android.*` class throws "not mocked" in `src/test`. Consequences:
- `StreamResolutionResult.Video` builds an `Intent` in its constructor, so tests must use `mockk<StreamResolutionResult.Video>()`, never a real instance.
- Tasks 1, 2, 5 and 6 are verified by compilation plus the existing suite staying green, not by new JVM tests. Task 3 is the genuinely unit-testable piece and is where the test effort goes. This is deliberate, not an oversight.

**RxJava operator ordering matters for LiveData.** Follow `AnimeViewModel.resolve()` exactly: `.subscribeOn(io).observeOn(main).doOnSubscribe { ... }.doAfterTerminate { ... }.subscribeAndLogErrors(...)`. `doOnSubscribe` must sit *after* `observeOn` or it runs on the IO thread and `MutableLiveData.value =` throws.

**AutoDispose does not apply here.** `.autoDisposable(scope())` is for Activity/Fragment-scoped subscriptions. ViewModels in this codebase hold `Disposable` fields and dispose them in `onCleared()` — follow that.

---

## File Structure

| File | Responsibility |
|---|---|
| `anime/resolver/StreamResolutionResult.kt` (modify) | Owns the intent contract between resolver output and the player. Gains `AnimeStreamContext`. |
| `anime/stream/StreamEpisodeViewModel.kt` (create) | Resolves a neighbouring episode to a playable `Video`; bookmarks. The only new unit-tested logic. |
| `anime/stream/StreamPlayerManager.kt` (modify) | Playback mechanics. Gains an "ended" signal and a start-position on reset. |
| `anime/stream/StreamActivity.kt` (modify) | Intent/lifecycle owner. Gains the in-place episode swap. |
| `anime/stream/StreamScreen.kt` (modify) | All player UI: the two new toolbar buttons and the countdown card. |
| `anime/AnimeScreen.kt` (modify) | Supplies `episodeAmount` + `hosterName` when launching the player. |
| `util/data/PreferenceHelper.kt`, `settings/SettingsScreen.kt` (modify) | The autoplay setting. |
| `test/.../anime/stream/StreamEpisodeViewModelTest.kt` (create) | Tests for the candidate walk and bookmark gating. |

---

### Task 1: Intent contract — `AnimeStreamContext` with episode count and hoster

**Goal:** The player receives `episodeAmount` and `hosterName`, delivered through a single context object instead of a growing positional parameter list.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/resolver/StreamResolutionResult.kt:17-71`
- Modify: `src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt:129-155`, `:252-259`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt:60-98`, `:276-291`
- Modify: `src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt:78`

**Acceptance Criteria:**
- [ ] `AnimeStreamContext` carries `id`, `name`, `episode`, `episodeAmount`, `language`, `coverUri`, `hosterName`.
- [ ] `Video.makeIntent`/`Video.play` take `AnimeStreamContext?` plus `forceInternal`, replacing the seven loose parameters.
- [ ] `StreamActivity.episodeAmount` returns `-1` when the extra is absent; `StreamActivity.hosterName` returns `null`.
- [ ] `AnimeScreen` passes the real `episodeAmount` and the hoster of the stream the user actually tapped.
- [ ] `StreamActivity.openInOtherApp` and `TvStreamScreen` still compile and behave as before.

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add the context class and new extras in `StreamResolutionResult.kt`**

Add above `sealed class StreamResolutionResult`:

```kotlin
/**
 * Everything the internal player needs to know about the episode it is playing, beyond the
 * resolved video URL itself. Passed as intent extras by [StreamResolutionResult.Video.makeIntent].
 */
data class AnimeStreamContext(
    val id: String,
    val name: String?,
    val episode: Int,
    val episodeAmount: Int,
    val language: AnimeLanguage,
    val coverUri: Uri?,
    val hosterName: String?,
)
```

Inside `Video.companion object`, add to the existing extras:

```kotlin
            const val EPISODE_AMOUNT_EXTRA = "episode_amount"
            const val HOSTER_NAME_EXTRA = "hoster_name"
```

- [ ] **Step 2: Replace `makeIntent`/`play` parameter lists**

Replace the existing `makeIntent` and `play` functions (currently lines 44-70) with:

```kotlin
        fun makeIntent(
            context: Context,
            streamContext: AnimeStreamContext? = null,
            forceInternal: Boolean = false,
        ): Intent = intent
            .apply { if (forceInternal) component = ComponentName(context, StreamActivity::class.java) }
            .apply {
                if (streamContext != null) {
                    putExtra(ID_EXTRA, streamContext.id)
                    putExtra(EPISODE_EXTRA, streamContext.episode)
                    putExtra(EPISODE_AMOUNT_EXTRA, streamContext.episodeAmount)
                    putExtra(LANGUAGE_EXTRA, streamContext.language)

                    if (streamContext.name != null) putExtra(NAME_EXTRA, streamContext.name)
                    if (streamContext.coverUri != null) putExtra(COVER_EXTRA, streamContext.coverUri)
                    if (streamContext.hosterName != null) putExtra(HOSTER_NAME_EXTRA, streamContext.hosterName)
                }
            }

        fun play(
            context: Context,
            streamContext: AnimeStreamContext? = null,
            forceInternal: Boolean = false,
        ) {
            context.startActivity(makeIntent(context, streamContext, forceInternal))
        }
```

- [ ] **Step 3: Add the accessors in `StreamActivity.kt`**

Add imports `EPISODE_AMOUNT_EXTRA` and `HOSTER_NAME_EXTRA` alongside the existing extras imports, then add after the existing `episode` property (line 66-67):

```kotlin
    internal val episodeAmount: Int
        get() = intent.getIntExtra(EPISODE_AMOUNT_EXTRA, -1)

    internal val hosterName: String?
        get() = intent.getStringExtra(HOSTER_NAME_EXTRA)
```

- [ ] **Step 4: Update `openInOtherApp` for the new signature**

In `StreamActivity.openInOtherApp` (line 280) the call is `.makeIntent(this)`. It passes no context and needs no change — confirm it still compiles, since `streamContext` now defaults to `null`.

- [ ] **Step 5: Track the tapped stream in `AnimeScreen.kt`**

Add next to the other `remember` declarations (near line 113):

```kotlin
    var playingStream by remember { mutableStateOf<AnimeStream?>(null) }
```

In the `onPlay` handler (line 252-259), record the stream in both branches so the hoster survives the no-wifi dialog:

```kotlin
        onPlay = { stream ->
            val connectivityManager = requireNotNull(context.getSystemService<ConnectivityManager>())
            if (connectivityManager.isConnectedToCellular && preferenceHelper.shouldCheckCellular) {
                noWifiStream = stream
            } else {
                playingStream = stream
                viewModel.resolve(stream)
            }
        },
```

and in `onConfirmNoWifi` (line 266-271):

```kotlin
        onConfirmNoWifi = {
            if (noWifiRemember) preferenceHelper.shouldCheckCellular = false
            noWifiStream?.let {
                playingStream = it
                viewModel.resolve(it)
            }
            noWifiStream = null
            noWifiRemember = false
        },
```

- [ ] **Step 6: Build the context at launch in `AnimeScreen.kt`**

Replace the `is StreamResolutionResult.Video ->` branch (lines 131-141) with:

```kotlin
            is StreamResolutionResult.Video -> {
                result.play(
                    context,
                    AnimeStreamContext(
                        id = id,
                        name = name,
                        episode = episode,
                        episodeAmount = episodeAmount ?: -1,
                        language = language,
                        coverUri = Uri.parse(ProxerUrls.entryImage(id).toString()),
                        hosterName = playingStream?.hosterName,
                    ),
                    forceInternal = true,
                )
            }
```

Add `import me.proxer.app.anime.resolver.AnimeStreamContext`.

- [ ] **Step 7: Update `TvStreamScreen.kt`**

Replace the `result.play(...)` call (lines 78-87) with:

```kotlin
                result.play(
                    context,
                    AnimeStreamContext(
                        id = entryId,
                        name = entryName,
                        episode = episode,
                        episodeAmount = -1,
                        language = language,
                        coverUri = ProxerUrls.entryImage(entryId).androidUri(),
                        hosterName = null,
                    ),
                    forceInternal = true,
                )
```

`episodeAmount = -1` and `hosterName = null` make the TV frontend opt out of the new controls — it is out of scope for this feature and gets its own pass later. Add `import me.proxer.app.anime.resolver.AnimeStreamContext`.

- [ ] **Step 8: Verify and commit**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/kotlin/me/proxer/app/anime src/main/kotlin/me/proxer/app/tv
git commit -m "refactor(anime): pass episode context to the player as one object"
```

---

### Task 2: Player manager — playback-ended signal and resettable start position

**Goal:** `StreamPlayerManager` reports when an episode finishes and can be reset to a specific start position.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamPlayerManager.kt:74-93`, `:199-266`

**Acceptance Criteria:**
- [ ] `playbackEndedSubject: PublishSubject<Unit>` emits exactly once per transition into `Player.STATE_ENDED`.
- [ ] The existing `PlayerState.PAUSING` emission on `STATE_ENDED` is unchanged.
- [ ] `reset(startPosition: Long = -1)` seeds `lastPosition` with `startPosition` so the new media starts there.

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add the subject**

Next to the existing subjects (line 202-204):

```kotlin
    val playbackEndedSubject = PublishSubject.create<Unit>()
```

- [ ] **Step 2: Emit on `STATE_ENDED`**

In `eventListener.onPlaybackStateChanged`, change the `STATE_ENDED` branch (lines 80-82) to:

```kotlin
                    Player.STATE_ENDED -> {
                        playerStateSubject.onNext(PlayerState.PAUSING)
                        playbackEndedSubject.onNext(Unit)
                    }
```

Media3 only calls `onPlaybackStateChanged` on an actual state *transition*, so this fires once per completed episode. The duplicate-suppression guard for the countdown lives in Task 6 regardless, because a cast handover can produce a second `STATE_ENDED`.

- [ ] **Step 3: Give `reset` a start position**

Change `reset()` (lines 254-266) to:

```kotlin
    fun reset(startPosition: Long = -1) {
        currentPlayer.playWhenReady = false

        wasPlaying = false
        lastPosition = startPosition

        localMediaSource = buildLocalMediaSourceWithAds(client, uri)
        castMediaItem = buildCastMediaItem(name, episode, coverUri, uri)

        retry()

        if (startPosition > 0) {
            currentPlayer.seekTo(startPosition)
        }

        currentPlayer.playWhenReady = true
    }
```

The explicit `seekTo` is needed for the local player: `retry()` only seeks for the cast player, and `play()` (which normally applies `lastPosition`) is not called on this path.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/kotlin/me/proxer/app/anime/stream/StreamPlayerManager.kt
git commit -m "feat(anime): signal playback end and allow reset to a start position"
```

---

### Task 3: `StreamEpisodeViewModel` with tests

**Goal:** A ViewModel that resolves a neighbouring episode to a playable `Video`, preferring the current hoster, and bookmarks when configured to.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModel.kt`
- Create: `src/test/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModelTest.kt`
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt:325`

**Acceptance Criteria:**
- [ ] `navigateTo(episode, preferredHoster)` emits `EpisodeNavigationTarget(episode, video)` on `episodeNavigationResult`.
- [ ] The preferred hoster is tried first when that episode has it.
- [ ] Candidates resolving to `Link`, `App` or `Message`, and candidates whose resolver errors, are skipped rather than being fatal.
- [ ] Streams with no resolver, or whose resolver sets `ignore`, are never attempted.
- [ ] Non-public streams are skipped when logged out.
- [ ] When no candidate yields a `Video`, `episodeNavigationError` is set and `episodeNavigationResult` stays null.
- [ ] `bookmark(episode)` calls the endpoint only when `areBookmarksAutomatic` and `isLoggedIn` are both true.
- [ ] `isNavigating` is false after the call terminates.

**Verify:** `./gradlew testDebugUnitTest --tests "me.proxer.app.anime.stream.StreamEpisodeViewModelTest"` → all tests pass

**Steps:**

- [ ] **Step 1: Write the failing test file**

Create `src/test/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModelTest.kt`:

```kotlin
package me.proxer.app.anime.stream

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.reactivex.Single
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.anime.resolver.StreamResolver
import me.proxer.app.anime.resolver.StreamResolverFactory
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.anime.StreamsEndpoint
import me.proxer.library.entity.anime.Stream
import me.proxer.library.enums.AnimeLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.KoinTestRule
import java.util.Date

class StreamEpisodeViewModelTest : KoinTest {

    private companion object {
        private const val ENTRY_ID = "12345"
        private const val TARGET_EPISODE = 2
        private val LANGUAGE = AnimeLanguage.GERMAN_SUB
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val streamsEndpoint = mockk<StreamsEndpoint>(relaxed = true)

    private lateinit var viewModel: StreamEpisodeViewModel

    private fun stream(
        id: String,
        hosterName: String,
        isPublic: Boolean = true,
    ) = Stream(
        id,
        hosterName.lowercase(),
        hosterName,
        "image.png",
        "uploader-id",
        "Uploader",
        Date(0),
        null,
        null,
        false,
        isPublic,
    )

    /** A resolver whose [StreamResolver.resolve] emits [result]. */
    private fun resolver(result: StreamResolutionResult, ignore: Boolean = false): StreamResolver {
        val resolver = mockk<StreamResolver>(relaxed = true)

        every { resolver.ignore } returns ignore
        every { resolver.resolve(any()) } returns Single.just(result)

        return resolver
    }

    private fun failingResolver(): StreamResolver {
        val resolver = mockk<StreamResolver>(relaxed = true)

        every { resolver.ignore } returns false
        every { resolver.resolve(any()) } returns Single.error(IllegalStateException("boom"))

        return resolver
    }

    @Before
    fun setup() {
        mockkObject(StreamResolverFactory)

        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.areBookmarksAutomatic } returns true

        every { api.anime.streams(ENTRY_ID, TARGET_EPISODE, LANGUAGE) } returns streamsEndpoint
        every { streamsEndpoint.includeProxerStreams(true) } returns streamsEndpoint

        viewModel = StreamEpisodeViewModel(ENTRY_ID, LANGUAGE)
    }

    @After
    fun tearDown() {
        unmockkObject(StreamResolverFactory)
    }

    @Test
    fun `navigateTo emits the resolved video`() {
        val video = mockk<StreamResolutionResult.Video>()

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Proxer")))
        every { StreamResolverFactory.resolverFor("Proxer") } returns resolver(video)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertEquals(EpisodeNavigationTarget(TARGET_EPISODE, video), viewModel.episodeNavigationResult.value)
        assertNull(viewModel.episodeNavigationError.value)
        assertFalse(viewModel.isNavigating.value == true)
    }

    @Test
    fun `navigateTo prefers the hoster already in use`() {
        val preferredVideo = mockk<StreamResolutionResult.Video>()
        val otherVideo = mockk<StreamResolutionResult.Video>()

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Other"), stream("s2", "Proxer")))
        every { StreamResolverFactory.resolverFor("Other") } returns resolver(otherVideo)
        every { StreamResolverFactory.resolverFor("Proxer") } returns resolver(preferredVideo)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = "Proxer")

        assertEquals(
            EpisodeNavigationTarget(TARGET_EPISODE, preferredVideo),
            viewModel.episodeNavigationResult.value,
        )
    }

    @Test
    fun `navigateTo falls back when the preferred hoster is absent`() {
        val video = mockk<StreamResolutionResult.Video>()

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Other")))
        every { StreamResolverFactory.resolverFor("Other") } returns resolver(video)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = "Proxer")

        assertEquals(EpisodeNavigationTarget(TARGET_EPISODE, video), viewModel.episodeNavigationResult.value)
    }

    @Test
    fun `navigateTo skips non-video results`() {
        val video = mockk<StreamResolutionResult.Video>()

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Netflix"), stream("s2", "Proxer")))
        every { StreamResolverFactory.resolverFor("Netflix") } returns
            resolver(StreamResolutionResult.Message("not playable here"))
        every { StreamResolverFactory.resolverFor("Proxer") } returns resolver(video)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertEquals(EpisodeNavigationTarget(TARGET_EPISODE, video), viewModel.episodeNavigationResult.value)
    }

    @Test
    fun `navigateTo skips candidates whose resolver fails`() {
        val video = mockk<StreamResolutionResult.Video>()

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Broken"), stream("s2", "Proxer")))
        every { StreamResolverFactory.resolverFor("Broken") } returns failingResolver()
        every { StreamResolverFactory.resolverFor("Proxer") } returns resolver(video)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertEquals(EpisodeNavigationTarget(TARGET_EPISODE, video), viewModel.episodeNavigationResult.value)
    }

    @Test
    fun `navigateTo ignores hosters without a resolver and ignored resolvers`() {
        streamsEndpoint.stubSuccess(listOf(stream("s1", "Unknown"), stream("s2", "Ignored")))
        every { StreamResolverFactory.resolverFor("Unknown") } returns null
        every { StreamResolverFactory.resolverFor("Ignored") } returns
            resolver(mockk<StreamResolutionResult.Video>(), ignore = true)

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertNull(viewModel.episodeNavigationResult.value)
        assertNotNull(viewModel.episodeNavigationError.value)
    }

    @Test
    fun `navigateTo skips non-public streams when logged out`() {
        every { storageHelper.isLoggedIn } returns false

        streamsEndpoint.stubSuccess(listOf(stream("s1", "Proxer", isPublic = false)))
        every { StreamResolverFactory.resolverFor("Proxer") } returns
            resolver(mockk<StreamResolutionResult.Video>())

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertNull(viewModel.episodeNavigationResult.value)
        assertNotNull(viewModel.episodeNavigationError.value)
    }

    @Test
    fun `navigateTo sets an error when nothing resolves to a video`() {
        streamsEndpoint.stubSuccess(listOf(stream("s1", "Broken")))
        every { StreamResolverFactory.resolverFor("Broken") } returns failingResolver()

        viewModel.navigateTo(TARGET_EPISODE, preferredHoster = null)

        assertNull(viewModel.episodeNavigationResult.value)
        assertNotNull(viewModel.episodeNavigationError.value)
        assertFalse(viewModel.isNavigating.value == true)
    }

    @Test
    fun `bookmark does nothing when automatic bookmarks are off`() {
        every { preferenceHelper.areBookmarksAutomatic } returns false

        viewModel.bookmark(TARGET_EPISODE)

        verify(exactly = 0) { api.ucp.setBookmark(any(), any(), any(), any()) }
    }

    @Test
    fun `bookmark does nothing when logged out`() {
        every { storageHelper.isLoggedIn } returns false

        viewModel.bookmark(TARGET_EPISODE)

        verify(exactly = 0) { api.ucp.setBookmark(any(), any(), any(), any()) }
    }

    @Test
    fun `bookmark calls the endpoint when enabled and logged in`() {
        viewModel.bookmark(TARGET_EPISODE)

        verify { api.ucp.setBookmark(ENTRY_ID, TARGET_EPISODE, any(), any()) }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.anime.stream.StreamEpisodeViewModelTest"`
Expected: FAIL — compilation error, `Unresolved reference: StreamEpisodeViewModel`.

- [ ] **Step 3: Write the ViewModel**

Create `src/main/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModel.kt`:

```kotlin
package me.proxer.app.anime.stream

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.anime.resolver.StreamResolverFactory
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.ResettingMutableLiveData
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.extension.toMediaLanguage
import me.proxer.library.ProxerApi
import me.proxer.library.entity.anime.Stream
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category

/** A neighbouring episode together with the stream that was successfully resolved for it. */
data class EpisodeNavigationTarget(val episode: Int, val video: StreamResolutionResult.Video)

/**
 * Resolves neighbouring episodes for the internal player.
 *
 * Deliberately not a [me.proxer.app.base.BaseViewModel]: that base class models "load this
 * screen's single payload", whereas episode navigation is an on-demand side channel fired by a
 * button press or by playback ending. This mirrors [me.proxer.app.anime.AnimeViewModel.resolve].
 */
class StreamEpisodeViewModel(
    private val entryId: String,
    private val language: AnimeLanguage,
) : ViewModel() {

    private val api by safeInject<ProxerApi>()
    private val storageHelper by safeInject<StorageHelper>()
    private val preferenceHelper by safeInject<PreferenceHelper>()

    val episodeNavigationResult = ResettingMutableLiveData<EpisodeNavigationTarget>()
    val episodeNavigationError = ResettingMutableLiveData<ErrorAction>()
    val isNavigating = MutableLiveData(false)

    private var navigationDisposable: Disposable? = null
    private var bookmarkDisposable: Disposable? = null

    override fun onCleared() {
        navigationDisposable?.dispose()
        bookmarkDisposable?.dispose()

        navigationDisposable = null
        bookmarkDisposable = null

        super.onCleared()
    }

    fun navigateTo(episode: Int, preferredHoster: String?) {
        navigationDisposable?.dispose()

        navigationDisposable =
            api.anime
                .streams(entryId, episode, language)
                .includeProxerStreams(true)
                .buildSingle()
                .map { streams -> orderCandidates(streams, preferredHoster) }
                .flatMap { candidates -> resolveFirstVideo(candidates) }
                .map { video -> EpisodeNavigationTarget(episode, video) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { isNavigating.value = true }
                .doAfterTerminate { isNavigating.value = false }
                .subscribeAndLogErrors(
                    {
                        episodeNavigationError.value = null
                        episodeNavigationResult.value = it
                    },
                    {
                        episodeNavigationResult.value = null
                        episodeNavigationError.value = ErrorUtils.handle(it)
                    },
                )
    }

    fun bookmark(episode: Int) {
        if (!preferenceHelper.areBookmarksAutomatic || !storageHelper.isLoggedIn) return

        bookmarkDisposable?.dispose()
        bookmarkDisposable =
            api.ucp
                .setBookmark(entryId, episode, language.toMediaLanguage(), Category.ANIME)
                .buildSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeAndLogErrors()
    }

    /**
     * Keeps only streams the internal player could actually use, one per hoster, with
     * [preferredHoster] first. The sort is stable, so the remaining order matches the API's.
     */
    private fun orderCandidates(streams: List<Stream>, preferredHoster: String?): List<Stream> = streams
        .filter { stream ->
            val resolver = StreamResolverFactory.resolverFor(stream.hosterName)

            resolver != null && !resolver.ignore
        }.filter { stream -> stream.isPublic || storageHelper.isLoggedIn }
        .groupBy { stream -> stream.hoster }
        .map { (_, group) -> group.first() }
        .sortedByDescending { stream -> stream.hosterName.equals(preferredHoster, ignoreCase = true) }

    /**
     * Resolves [candidates] one at a time and emits the first that yields a playable
     * [StreamResolutionResult.Video]. Non-video results and resolver failures are skipped rather
     * than aborting the walk — a hoster that hands back a Link or an App entry is normal.
     */
    private fun resolveFirstVideo(candidates: List<Stream>): Single<StreamResolutionResult.Video> = Observable
        .fromIterable(candidates)
        .concatMapMaybe { stream ->
            val resolver = StreamResolverFactory.resolverFor(stream.hosterName)

            if (resolver == null) {
                Maybe.empty()
            } else {
                resolver
                    .resolve(stream.id)
                    .flatMapMaybe { result ->
                        when (result) {
                            is StreamResolutionResult.Video -> Maybe.just(result)
                            else -> Maybe.empty()
                        }
                    }.onErrorComplete()
            }
        }.firstElement()
        .switchIfEmpty(Single.error(StreamResolutionException()))
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.anime.stream.StreamEpisodeViewModelTest"`
Expected: PASS, 11 tests.

If `Stream`'s constructor arity does not match, inspect it with:
`javap -cp ~/.gradle/caches/modules-2/files-2.1/com.github.proxer/ProxerLibJava/5.4.0/*/ProxerLibJava-5.4.0.jar me.proxer.library.entity.anime.Stream`

- [ ] **Step 5: Register in Koin**

In `src/main/kotlin/me/proxer/app/MainModules.kt`, after the `AnimeViewModel` registration (line 323-325):

```kotlin
        viewModel { (entryId: String, language: AnimeLanguage) ->
            StreamEpisodeViewModel(entryId, language)
        }
```

Add `import me.proxer.app.anime.stream.StreamEpisodeViewModel`.

- [ ] **Step 6: Verify the whole suite and commit**

Run: `./gradlew testDebugUnitTest detekt`
Expected: `BUILD SUCCESSFUL`, 376 tests (365 existing + 11 new), 0 failures.

```bash
git add src/main/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModel.kt \
        src/test/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModelTest.kt \
        src/main/kotlin/me/proxer/app/MainModules.kt
git commit -m "feat(anime): add view model resolving neighbouring episodes"
```

---

### Task 4: Autoplay setting

**Goal:** Users can turn autoplay off; it defaults to on.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/data/PreferenceHelper.kt:33-43`, `:108-118`
- Modify: `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt:64`, `:279`, `:306`, `:360`, `:380`, `:441-459`, `:706`, `:726`
- Modify: `src/main/res/values/strings.xml`

**Acceptance Criteria:**
- [ ] `PreferenceHelper.isAutoplayNextEpisodeEnabled` reads/writes key `autoplay_next_episode`, defaulting to `true`.
- [ ] The settings screen shows a switch bound to it, placed directly after the auto-bookmark row.
- [ ] `SettingsContentPreview` compiles with the new parameters.

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add the preference**

In `PreferenceHelper.companion object`, next to `AUTO_BOOKMARK`:

```kotlin
        const val AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
```

After the `areBookmarksAutomatic` property:

```kotlin
    var isAutoplayNextEpisodeEnabled
        get() = sharedPreferences.getBoolean(AUTOPLAY_NEXT_EPISODE, true)
        set(value) {
            sharedPreferences.edit { putBoolean(AUTOPLAY_NEXT_EPISODE, value) }
        }
```

- [ ] **Step 2: Add the strings**

In `src/main/res/values/strings.xml`, next to the `preference_auto_bookmark_*` entries (German, matching the project's single locale):

```xml
    <string name="preference_autoplay_next_episode_title">Nächste Episode automatisch abspielen</string>
    <string name="preference_autoplay_next_episode_summary_on">Die nächste Episode wird nach dem Ende automatisch gestartet</string>
    <string name="preference_autoplay_next_episode_summary_off">Die nächste Episode wird nicht automatisch gestartet</string>
```

- [ ] **Step 3: Wire the switch into `SettingsScreen`**

Add the state near line 64:

```kotlin
    var autoplayNextEpisode by remember { mutableStateOf(preferenceHelper.isAutoplayNextEpisodeEnabled) }
```

Pass it to `SettingsContent` (next to `autoBookmark = autoBookmark,` at line 279):

```kotlin
        autoplayNextEpisode = autoplayNextEpisode,
```

Add the handler next to `onAutoBookmarkChange` (line 306):

```kotlin
        onAutoplayNextEpisodeChange = {
            autoplayNextEpisode = it
            preferenceHelper.isAutoplayNextEpisodeEnabled = it
        },
```

Add the parameters to `SettingsContent` — `autoplayNextEpisode: Boolean,` next to `autoBookmark: Boolean,` (line 360) and `onAutoplayNextEpisodeChange: (Boolean) -> Unit,` next to `onAutoBookmarkChange` (line 380).

Add this `item` immediately after the auto-bookmark item (which ends at line 459):

```kotlin
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_autoplay_next_episode_title)) },
                    supportingContent = {
                        Text(
                            if (autoplayNextEpisode) {
                                stringResource(R.string.preference_autoplay_next_episode_summary_on)
                            } else {
                                stringResource(R.string.preference_autoplay_next_episode_summary_off)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoplayNextEpisode,
                            onCheckedChange = onAutoplayNextEpisodeChange,
                        )
                    },
                )
            }
```

Finally add `autoplayNextEpisode = true,` and `onAutoplayNextEpisodeChange = {},` to the preview composable (lines 706 and 726).

- [ ] **Step 4: Verify and commit**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/kotlin/me/proxer/app/util/data/PreferenceHelper.kt \
        src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt \
        src/main/res/values/strings.xml
git commit -m "feat(settings): add autoplay next episode preference"
```

---

### Task 5: In-place episode swap in `StreamActivity`

**Goal:** The activity can replace the playing episode without restarting, with correct position bookkeeping on both sides of the swap.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt:162-197`

**Acceptance Criteria:**
- [ ] `switchToEpisode(episode, video, clearOutgoingPosition)` saves the outgoing episode's position, swaps the intent, and restarts playback at the incoming episode's stored position.
- [ ] When `clearOutgoingPosition` is true the outgoing episode's stored position is written as `0`, so reopening it does not resume at the credits.
- [ ] `onNewIntent` and `switchToEpisode` share one implementation.
- [ ] `hasNextEpisode` / `hasPreviousEpisode` reflect `episode` against `episodeAmount`, and are false when `episodeAmount` is `-1`.

**Verify:** `./gradlew compileDebugKotlin detekt` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add the neighbour predicates**

After the `hosterName` accessor from Task 1:

```kotlin
    internal val hasPreviousEpisode: Boolean
        get() = episodeAmount > 0 && episode > 1

    internal val hasNextEpisode: Boolean
        get() = episodeAmount > 0 && episode < episodeAmount
```

- [ ] **Step 2: Extract the shared intent-application path**

Replace `onNewIntent` (lines 188-197) with:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.data != null && intent.data != this.intent.data) {
            applyStreamIntent(intent)
        }
    }

    /**
     * Swaps in a new episode without recreating the activity. The outgoing position is persisted
     * first: [clearOutgoingPosition] is set when the episode ended naturally, so that reopening it
     * later starts from the beginning instead of the credits.
     */
    internal fun switchToEpisode(
        episode: Int,
        video: StreamResolutionResult.Video,
        clearOutgoingPosition: Boolean = false,
    ) {
        storageHelper.putLastAnimePosition(
            id,
            this.episode,
            language,
            if (clearOutgoingPosition) 0 else playerManager.currentPlayer.currentPosition,
        )

        val streamContext =
            AnimeStreamContext(
                id = id,
                name = name,
                episode = episode,
                episodeAmount = episodeAmount,
                language = language,
                coverUri = coverUri,
                hosterName = hosterName,
            )

        applyStreamIntent(video.makeIntent(this, streamContext, forceInternal = true))
    }

    private fun applyStreamIntent(intent: Intent) {
        this.intent = intent

        playerManager.reset(storageHelper.getLastAnimePosition(id, episode, language) ?: -1)
        contentKey++
    }
```

Note the ordering inside `applyStreamIntent`: `this.intent` is assigned *first*, so `id`, `episode` and `language` already read from the new intent when the stored position is looked up.

Add imports for `me.proxer.app.anime.resolver.AnimeStreamContext` and `me.proxer.app.anime.resolver.StreamResolutionResult` (the latter is already imported).

- [ ] **Step 3: Verify and commit**

Run: `./gradlew compileDebugKotlin detekt`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt
git commit -m "feat(anime): swap episodes in place in the stream activity"
```

---

### Task 6: Player UI — navigation buttons and autoplay countdown

**Goal:** The player shows next/previous buttons when those episodes exist, and counts down into the next episode when one finishes.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt:66-271`, `:273-441`, `:443-473`
- Modify: `src/main/res/values/strings.xml`

**Acceptance Criteria:**
- [ ] Previous/next `IconButton`s appear in the overlay toolbar only when that neighbour exists.
- [ ] Pressing one calls `navigateTo`, and the resolved result swaps the episode.
- [ ] Advancing forwards (button or autoplay) calls `bookmark(newEpisode)`; going backwards does not.
- [ ] When an episode ends with autoplay enabled and a next episode available, a countdown card appears and advances after it elapses.
- [ ] The countdown is cancellable, and does not run while the activity is not resumed.
- [ ] A second `STATE_ENDED` emission for the same episode does not start a second countdown.
- [ ] Navigation failures show a toast and leave playback untouched.

**Verify:** `./gradlew compileDebugKotlin detekt testDebugUnitTest` → `BUILD SUCCESSFUL`, 376 tests, 0 failures

**Steps:**

- [ ] **Step 1: Add the strings**

In `src/main/res/values/strings.xml`:

```xml
    <string name="stream_autoplay_countdown">Nächste Episode in %1$d…</string>
    <string name="stream_autoplay_play_now">Jetzt abspielen</string>
```

`fragment_anime_next_episode` ("Nächste Episode") and `fragment_anime_previous_episode` ("Vorherige Episode") already exist and are reused for the button content descriptions.

- [ ] **Step 2: Wire the ViewModel and autoplay state into `StreamScreen`**

Add these imports:

```kotlin
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import me.proxer.app.util.extension.toast
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
```

Inside `StreamScreen`, after the existing `remember` declarations (around line 84):

```kotlin
    val preferenceHelper: PreferenceHelper = koinInject()

    val episodeViewModel = koinViewModel<StreamEpisodeViewModel> {
        parametersOf(activity.id, activity.language)
    }

    // Non-null while a countdown is running; holds the remaining seconds.
    var autoplaySecondsLeft by remember { mutableStateOf<Int?>(null) }
    // Guards against a second STATE_ENDED for the same episode restarting the countdown.
    var endedEpisode by remember { mutableStateOf<Int?>(null) }
    // The episode a pending navigation is heading to, so the result handler knows whether to bookmark.
    var pendingEpisode by remember { mutableStateOf<Int?>(null) }

    val isNavigating by episodeViewModel.isNavigating.observeAsState(false)
```

Add `import me.proxer.app.util.data.PreferenceHelper` and `import org.koin.compose.koinInject`.

Define the shared advance/navigate helper right after:

```kotlin
    fun navigateToEpisode(episode: Int) {
        autoplaySecondsLeft = null
        pendingEpisode = episode

        episodeViewModel.navigateTo(episode, activity.hosterName)
    }
```

- [ ] **Step 3: Observe the ViewModel and the playback-ended signal**

Add to the existing `DisposableEffect(playerManager)` block, next to the other `disposables.add(...)` calls:

```kotlin
        disposables.add(
            playerManager.playbackEndedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val current = activity.episode

                    if (endedEpisode != current) {
                        endedEpisode = current

                        if (preferenceHelper.isAutoplayNextEpisodeEnabled && activity.hasNextEpisode) {
                            autoplaySecondsLeft = 5
                        }
                    }
                },
        )
```

After the `DisposableEffect` block, observe the navigation outcome:

```kotlin
    ObserveLiveDataEvent(episodeViewModel.episodeNavigationResult) { target ->
        val wasAutoplay = endedEpisode == activity.episode

        if (target.episode > activity.episode) {
            episodeViewModel.bookmark(target.episode)
        }

        endedEpisode = null
        pendingEpisode = null

        activity.switchToEpisode(target.episode, target.video, clearOutgoingPosition = wasAutoplay)
    }

    ObserveLiveDataEvent(episodeViewModel.episodeNavigationError) { action ->
        pendingEpisode = null
        autoplaySecondsLeft = null

        activity.toast(action.message)
    }
```

Add `import me.proxer.app.ui.compose.ObserveLiveDataEvent`.

`switchToEpisode` must be the **last** statement — it reassigns the intent, so `activity.episode` changes underneath anything after it.

- [ ] **Step 4: Drive the countdown, gated on the lifecycle**

After the existing auto-hide `LaunchedEffect`s (around line 182):

```kotlin
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(autoplaySecondsLeft != null) {
        if (autoplaySecondsLeft == null) return@LaunchedEffect

        // repeatOnLifecycle suspends while the activity is stopped, so a backgrounded player
        // cannot chain-load episodes; the countdown resumes where it left off.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val remaining = autoplaySecondsLeft ?: break

                if (remaining <= 0) {
                    navigateToEpisode(activity.episode + 1)
                    break
                }

                delay(1_000)
                autoplaySecondsLeft = (autoplaySecondsLeft ?: break) - 1
            }
        }
    }
```

- [ ] **Step 5: Pass the new state into `StreamContent`**

Add these arguments to the existing `StreamContent(...)` call:

```kotlin
        hasPreviousEpisode = activity.hasPreviousEpisode,
        hasNextEpisode = activity.hasNextEpisode,
        isNavigating = isNavigating == true,
        autoplaySecondsLeft = autoplaySecondsLeft,
        onPreviousEpisode = { navigateToEpisode(activity.episode - 1) },
        onNextEpisode = { navigateToEpisode(activity.episode + 1) },
        onCancelAutoplay = { autoplaySecondsLeft = null },
        onPlayNextNow = { navigateToEpisode(activity.episode + 1) },
```

- [ ] **Step 6: Render the buttons and the countdown in `StreamContent`**

Add the parameters to `StreamContent`'s signature, after `isProxerStream: Boolean,`:

```kotlin
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    isNavigating: Boolean,
    autoplaySecondsLeft: Int?,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onCancelAutoplay: () -> Unit,
    onPlayNextNow: () -> Unit,
```

In the toolbar's `actions = { ... }` block, before the existing open-in-other-app button:

```kotlin
                    if (hasPreviousEpisode) {
                        IconButton(onClick = onPreviousEpisode, enabled = !isNavigating) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.fragment_anime_previous_episode),
                                tint = Color.White,
                            )
                        }
                    }
                    if (hasNextEpisode) {
                        IconButton(onClick = onNextEpisode, enabled = !isNavigating) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.fragment_anime_next_episode),
                                tint = Color.White,
                            )
                        }
                    }
```

Inside the root `Box`, after the volume/brightness overlay and before the toolbar:

```kotlin
        // Autoplay countdown
        AnimatedVisibility(
            visible = autoplaySecondsLeft != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.stream_autoplay_countdown,
                            autoplaySecondsLeft ?: 0,
                        ),
                    )
                    Row {
                        TextButton(onClick = onCancelAutoplay) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(onClick = onPlayNextNow) {
                            Text(stringResource(R.string.stream_autoplay_play_now))
                        }
                    }
                }
            }
        }
```

The `bottom = 72.dp` inset keeps the card clear of the Media3 controller's seek bar.

- [ ] **Step 7: Update the preview**

Add to `StreamContentPreview`:

```kotlin
            hasPreviousEpisode = true,
            hasNextEpisode = true,
            isNavigating = false,
            autoplaySecondsLeft = null,
            onPreviousEpisode = {},
            onNextEpisode = {},
            onCancelAutoplay = {},
            onPlayNextNow = {},
```

- [ ] **Step 8: Verify and commit**

Run: `./gradlew compileDebugKotlin detekt testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 376 tests, 0 failures.

```bash
git add src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt src/main/res/values/strings.xml
git commit -m "feat(anime): add episode navigation and autoplay to the player"
```

---

### Task 7: Full verification

**Goal:** The branch builds, passes static analysis, and the whole unit suite is green.

**Files:** none modified.

**Acceptance Criteria:**
- [ ] `./gradlew assembleDebug` succeeds.
- [ ] `./gradlew detekt lint` reports no new failures.
- [ ] `./gradlew testDebugUnitTest` passes with 376 tests, 0 failures.

**Verify:** `./gradlew assembleDebug detekt testDebugUnitTest` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Run the full build**

Run: `./gradlew assembleDebug detekt testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

If test counts differ from 376, count them explicitly rather than guessing:

```bash
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob('build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
print(f"tests={t} failures={f} errors={e}")
EOF
```

- [ ] **Step 2: Commit any fixes**

```bash
git add -A
git commit -m "fix(anime): address build and lint findings for episode navigation"
```

(Skip if nothing needed fixing.)

---

## Notes for the implementer

**`ObserveLiveDataEvent` and `ResettingMutableLiveData` go together.** `ResettingMutableLiveData` nulls itself once every observer has consumed a value, which is what makes these one-shot events rather than sticky state. Do not swap in a plain `MutableLiveData` — the navigation would re-fire on every recomposition.

**Why `endedEpisode` doubles as the autoplay marker.** In Task 6, `endedEpisode == activity.episode` is what tells the result handler that this navigation came from playback ending rather than a button press, which in turn decides whether the outgoing episode's stored position is cleared. Keep the two uses consistent if you refactor.

**Do not add `.autoDisposable(scope())` to the ViewModel subscriptions.** That pattern is for Activity-scoped subscriptions. `StreamEpisodeViewModel` disposes its own fields in `onCleared()`.

**If `koinViewModel` in `StreamScreen` fails at runtime** with a missing-definition error, check that Task 3 Step 5's registration landed in the `viewModelModule` block (not `applicationModules`) and that the parameter types match the call's `parametersOf(activity.id, activity.language)` exactly — Koin 4 matches injected parameters positionally by type.
