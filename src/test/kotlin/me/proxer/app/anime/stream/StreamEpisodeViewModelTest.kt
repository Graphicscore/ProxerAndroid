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
import org.koin.test.KoinTestRule
import org.koin.test.inject
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

    private fun stream(id: String, hosterName: String, isPublic: Boolean = true) = Stream(
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
