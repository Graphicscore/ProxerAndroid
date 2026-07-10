package me.proxer.app.media.episode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerCall
import me.proxer.library.api.info.EpisodeInfoEndpoint
import me.proxer.library.api.ucp.SetBookmarkEndpoint
import me.proxer.library.entity.info.AnimeEpisode
import me.proxer.library.entity.info.Episode
import me.proxer.library.entity.info.EpisodeInfo
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
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

class EpisodeViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: EpisodeViewModel

    private fun createEpisodeInfo(episodes: List<Episode>) = EpisodeInfo(
        firstEpisode = 1,
        lastEpisode = episodes.size,
        category = Category.ANIME,
        availableLanguages = setOf(MediaLanguage.GERMAN_SUB),
        userProgress = 0,
        episodes = episodes,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = EpisodeViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)
        val episodes = listOf(
            AnimeEpisode(1, MediaLanguage.GERMAN_SUB, setOf("hoster1"), listOf("image1")),
            AnimeEpisode(2, MediaLanguage.GERMAN_SUB, setOf("hoster1"), listOf("image1")),
        )

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(episodes))

        viewModel.load()

        assertEquals(2, viewModel.data.value?.size)
        assertEquals(listOf(1, 2), viewModel.data.value?.map { it.number })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(emptyList()))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)
        val firstEpisodes = listOf(AnimeEpisode(1, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()))
        val secondEpisodes = listOf(
            AnimeEpisode(1, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()),
            AnimeEpisode(2, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()),
        )

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(firstEpisodes))
        viewModel.load()
        assertEquals(1, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(createEpisodeInfo(secondEpisodes))
        viewModel.reload()

        assertEquals(2, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `bookmark sets bookmarkData on success`() {
        val bookmarkEndpoint = mockk<SetBookmarkEndpoint>(relaxed = true)
        val call = mockk<ProxerCall<Unit?>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns Unit

        every {
            api.ucp.setBookmark(entryId, 1, MediaLanguage.GERMAN_SUB, Category.ANIME)
        } returns bookmarkEndpoint
        every { bookmarkEndpoint.build() } returns call

        viewModel.bookmark(1, MediaLanguage.GERMAN_SUB, Category.ANIME)

        assertEquals(Unit, viewModel.bookmarkData.value)
        assertNull(viewModel.bookmarkError.value)
    }
}
