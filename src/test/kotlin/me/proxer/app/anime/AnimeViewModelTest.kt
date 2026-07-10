package me.proxer.app.anime

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.Validators
import me.proxer.library.ProxerApi
import me.proxer.library.api.anime.StreamsEndpoint
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
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

class AnimeViewModelTest : KoinTest {

    private companion object {
        private const val ENTRY_ID = "12345"
        private val LANGUAGE = AnimeLanguage.GERMAN_SUB
        private const val EPISODE = 1
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
    private val validators: Validators by inject()

    private val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
    private val streamsEndpoint = mockk<StreamsEndpoint>(relaxed = true)

    private lateinit var viewModel: AnimeViewModel

    private fun entryCore(medium: Medium = Medium.ANIMESERIES) = EntryCore(
        id = ENTRY_ID,
        name = "Test Anime",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = medium,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("99", "Related Manga", Medium.MANGASERIES),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true

        every { api.info.entryCore(ENTRY_ID) } returns entryEndpoint
        every { api.anime.streams(ENTRY_ID, EPISODE, LANGUAGE) } returns streamsEndpoint
        every { streamsEndpoint.includeProxerStreams(true) } returns streamsEndpoint

        viewModel = AnimeViewModel(ENTRY_ID, LANGUAGE, EPISODE)
    }

    @Test
    fun `load sets data on success`() {
        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertEquals(AnimeStreamInfo("Test Anime", 12, emptyList()), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        // The first attempt fails so entrySingle() never caches an EntryCore, keeping the reload observable
        // (once cached, entrySingle() bypasses api.info.entryCore() entirely on subsequent loads).
        entryEndpoint.stubError()
        viewModel.load()
        assertNotNull(viewModel.error.value)

        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())
        viewModel.reload()

        assertEquals(AnimeStreamInfo("Test Anime", 12, emptyList()), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when not confirmed`() {
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        entryEndpoint.stubSuccess(entryCore(medium = Medium.HENTAI))
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }

    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
}
