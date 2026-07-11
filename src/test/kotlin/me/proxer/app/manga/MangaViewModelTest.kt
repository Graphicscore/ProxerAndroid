package me.proxer.app.manga

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.api.manga.ChapterEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.entity.manga.Chapter
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
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
import java.util.Date

class MangaViewModelTest : KoinTest {

    private companion object {
        private const val ENTRY_ID = "9001"
        private val LANGUAGE = Language.GERMAN
        private const val EPISODE = 3
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

    private val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
    private val chapterEndpoint = mockk<ChapterEndpoint>(relaxed = true)

    private lateinit var viewModel: MangaViewModel

    // Shared so repeated chapter() calls stay structurally equal (Date() is otherwise non-deterministic).
    private val fixedDate = Date()

    private fun entryCore(medium: Medium = Medium.MANGASERIES) = EntryCore(
        id = ENTRY_ID,
        name = "Test Manga",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = medium,
        episodeAmount = 30,
        state = MediaState.FINISHED,
        ratingSum = 80,
        ratingAmount = 16,
        clicks = 200,
        category = Category.MANGA,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("50", "Related Anime", Medium.ANIMESERIES),
    )

    private fun chapter() = Chapter(
        id = "1",
        entryId = ENTRY_ID,
        title = "Chapter 3",
        uploaderId = "7",
        uploaderName = "Uploader",
        date = fixedDate,
        scanGroupId = "1",
        scanGroupName = "Scan Group",
        server = "https://example.com/manga",
        // Non-null pages makes Chapter.isOfficial false regardless of the server host, avoiding the
        // MangaLinkException/MangaNotAvailableException branches for this basic load/error/reload coverage.
        pages = emptyList(),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true

        every { api.info.entryCore(ENTRY_ID) } returns entryEndpoint
        every { api.manga.chapter(ENTRY_ID, EPISODE, LANGUAGE) } returns chapterEndpoint

        viewModel = MangaViewModel(ENTRY_ID, LANGUAGE, EPISODE)
    }

    @Test
    fun `load sets data on success`() {
        entryEndpoint.stubSuccess(entryCore())
        chapterEndpoint.stubSuccess(chapter())

        viewModel.load()

        assertEquals(MangaChapterInfo(chapter(), "Test Manga", 30), viewModel.data.value)
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
        chapterEndpoint.stubSuccess(chapter())

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
        chapterEndpoint.stubSuccess(chapter())
        viewModel.reload()

        assertEquals(MangaChapterInfo(chapter(), "Test Manga", 30), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when not confirmed`() {
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        entryEndpoint.stubSuccess(entryCore(medium = Medium.HMANGA))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
}
