package me.proxer.app.media.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.exception.AgeConfirmationRequiredException
import me.proxer.app.media.LocalTag
import me.proxer.app.media.TagDao
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.list.ListApi
import me.proxer.library.api.list.MediaSearchEndpoint
import me.proxer.library.api.list.TagListEndpoint
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.entity.list.Tag
import me.proxer.library.enums.MediaSearchSortCriteria
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.MediaType
import me.proxer.library.enums.Medium
import me.proxer.library.enums.TagSubType
import me.proxer.library.enums.TagType
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
import org.threeten.bp.Instant
import java.util.EnumSet

class MediaListViewModelTest : KoinTest {

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
    private val tagDao: TagDao by inject()

    // `api.list` is itself an unstubbed relaxed mock property. Pinning it to a single fixed mock instance
    // (instead of letting each call chain resolve it dynamically) keeps `verify { listApi.tagList() }`
    // unambiguous - verifying through the dynamic `api.list.tagList()` chain fails when `api.list` has
    // already been invoked multiple times (e.g. once per `load()`/`reload()` call for `mediaSearch()`).
    private val listApi = mockk<ListApi>(relaxed = true)

    private lateinit var viewModel: MediaListViewModel

    private fun entry(id: String) = MediaListEntry(
        id,
        "Entry $id",
        setOf("Action"),
        Medium.ANIMESERIES,
        12,
        MediaState.FINISHED,
        10,
        2,
        emptySet(),
    )

    private fun fullPage(prefix: String) = (0 until 30).map { entry("$prefix-$it") }

    private fun tag(id: String, type: TagType) = Tag(id, type, "Tag $id", "desc", TagSubType.OTHER, false)

    private fun localTag(id: String, type: TagType) = LocalTag(id, type, "Tag $id", "desc", TagSubType.OTHER, false)

    private fun mockMediaSearchEndpoint(): MediaSearchEndpoint {
        val endpoint = mockk<MediaSearchEndpoint>(relaxed = true)

        every { listApi.mediaSearch() } returns endpoint
        every { endpoint.sort(any()) } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.language(any()) } returns endpoint
        every { endpoint.type(any()) } returns endpoint
        every { endpoint.tagRateFilter(any()) } returns endpoint
        every { endpoint.tagSpoilerFilter(any()) } returns endpoint
        every { endpoint.fskConstraints(any()) } returns endpoint
        every { endpoint.hideFinished(any()) } returns endpoint
        every { endpoint.tags(any()) } returns endpoint
        every { endpoint.excludedTags(any()) } returns endpoint
        every { endpoint.genres(any()) } returns endpoint
        every { endpoint.excludedGenres(any()) } returns endpoint

        return endpoint
    }

    private fun newViewModel(type: MediaType = MediaType.ANIMESERIES) = MediaListViewModel(
        MediaSearchSortCriteria.RELEVANCE,
        type,
        null,
        null,
        emptyList(),
        emptyList(),
        EnumSet.noneOf(me.proxer.library.enums.FskConstraint::class.java),
        emptyList(),
        emptyList(),
        null,
        null,
        null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.list } returns listApi

        viewModel = newViewModel()
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockMediaSearchEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockMediaSearchEndpoint()
        val lastPage = listOf(entry("last-0"), entry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(entry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockMediaSearchEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(entry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed + listOf(original.first()), viewModel.data.value)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sortCriteria change triggers reload`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val resorted = listOf(entry("sorted-0"))
        endpoint.stubPagingSuccess(resorted)

        viewModel.sortCriteria = MediaSearchSortCriteria.NAME

        assertEquals(resorted, viewModel.data.value)
    }

    @Test
    fun `type change within the same hentai boundary reloads but does not reload tags`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val movieResult = listOf(entry("movie-0"))
        endpoint.stubPagingSuccess(movieResult)

        viewModel.type = MediaType.MOVIE

        assertEquals(movieResult, viewModel.data.value)
        verify(exactly = 0) { listApi.tagList() }
    }

    @Test
    fun `type change across the hentai boundary reloads and reloads tags`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val hentaiResult = listOf(entry("hentai-0"))
        endpoint.stubPagingSuccess(hentaiResult)

        every { tagDao.getTags() } returns emptyList()

        val tagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { listApi.tagList() } returns tagListEndpoint
        val hTagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { tagListEndpoint.type(TagType.H_TAG) } returns hTagListEndpoint
        tagListEndpoint.stubSuccess(emptyList())
        hTagListEndpoint.stubSuccess(emptyList())

        viewModel.type = MediaType.HENTAI

        assertEquals(hentaiResult, viewModel.data.value)
        verify { listApi.tagList() }
    }

    @Test
    fun `loadTags uses the cache directly when it is fresh`() {
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now()
        every { tagDao.getTags() } returns listOf(
            localTag("g1", TagType.GENRE),
            localTag("t1", TagType.TAG),
        )

        viewModel.loadTags()

        assertEquals(listOf("g1"), viewModel.genreData.value?.map { it.id })
        assertEquals(listOf("t1"), viewModel.tagData.value?.map { it.id })
        verify(exactly = 0) { listApi.tagList() }
    }

    @Test
    fun `loadTags fetches and persists remote tags when the cache is stale`() {
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now().minusSeconds(60 * 60 * 24 * 30)
        every { tagDao.getTags() } returns emptyList()

        val tagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { listApi.tagList() } returns tagListEndpoint
        val hTagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { tagListEndpoint.type(TagType.H_TAG) } returns hTagListEndpoint

        tagListEndpoint.stubSuccess(listOf(tag("g1", TagType.GENRE)))
        hTagListEndpoint.stubSuccess(listOf(tag("h1", TagType.H_TAG)))

        viewModel.loadTags()

        verify { tagDao.replaceTags(any()) }
        verify { preferenceHelper.lastTagUpdateDate = any() }
        assertEquals(listOf("g1"), viewModel.genreData.value?.map { it.id })
    }

    @Test
    fun `validate always requires age confirmation, regardless of type`() {
        val endpoint = mockMediaSearchEndpoint()
        every { validators.validateAgeConfirmation() } throws AgeConfirmationRequiredException()

        viewModel.load()

        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
}
