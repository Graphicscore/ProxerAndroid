package me.proxer.app.profile.comment

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.mockProxerCallNullableSuccess
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.comment.LocalComment
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.comment.UpdateCommentEndpoint
import me.proxer.library.api.user.UserCommentsEndpoint
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.entity.user.UserComment
import me.proxer.library.enums.Category
import me.proxer.library.enums.Medium
import me.proxer.library.enums.UserMediaProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.Instant
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ProfileCommentViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: ProfileCommentViewModel

    private fun comment(id: String) = UserComment(
        id,
        "entry-1",
        "Entry Name",
        Medium.ANIMESERIES,
        Category.ANIME,
        "author-$id",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment $id",
        5,
        1,
        0,
        Date(),
        "Author $id",
        "image.png",
    )

    private fun fullPage(prefix: String) = (0 until 10).map { comment("$prefix-$it") }

    private fun mockUserCommentsEndpoint(): UserCommentsEndpoint {
        val endpoint = mockk<UserCommentsEndpoint>(relaxed = true)

        every { api.user.comments("user-1", null) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.hasContent(*anyVararg()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        // ProfileCommentViewModel's dataSingle hops onto Schedulers.computation() while
        // mapping comments; force that scheduler onto the trampoline too.
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        // ProfileCommentViewModel's toParsedUserComment() transformation calls toSimpleBBTree(),
        // which otherwise invokes Android text classes (LinkifyCompat, SpannableStringBuilder).
        // Mock TextPrototype to avoid JUnit environment issues.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        viewModel = ProfileCommentViewModel("user-1", null, null)
    }

    @After
    fun teardown() {
        RxJavaPlugins.setComputationSchedulerHandler(null)
        unmockkObject(TextPrototype)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockUserCommentsEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockUserCommentsEndpoint()
        val lastPage = listOf(comment("last-0"), comment("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(comment("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockUserCommentsEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(comment("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `category change triggers reload`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val mangaOnly = listOf(comment("manga-0"))
        endpoint.stubPagingSuccess(mangaOnly)

        viewModel.category = Category.MANGA

        assertEquals(mangaOnly.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `deleteComment removes the item and deletes the draft on success`() {
        val endpoint = mockUserCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        every { updateEndpoint.build() } returns mockProxerCallNullableSuccess(null)

        viewModel.deleteComment(target)

        assertEquals(9, viewModel.data.value?.size)
        assertFalse(viewModel.data.value!!.any { it.id == target.id })
    }

    @Test
    fun `updateComment updates the matching entry in place`() {
        val endpoint = mockUserCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val update = LocalComment(
            target.id,
            target.entryId,
            UserMediaProgress.WILL_WATCH,
            RatingDetails(9, 8, 7, 6, 5),
            "Updated",
            9,
            target.episode,
        )

        viewModel.updateComment(update)

        val updated = viewModel.data.value!!.first { it.id == target.id }
        assertEquals(9, updated.overallRating)
    }
}
