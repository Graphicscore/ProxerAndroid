package me.proxer.app.forum

import android.content.res.Resources
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
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.forum.TopicEndpoint
import me.proxer.library.entity.forum.Post
import me.proxer.library.entity.forum.Topic
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

class TopicViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val topicEndpoint = mockk<TopicEndpoint>(relaxed = true)
    private lateinit var viewModel: TopicViewModel

    private fun post(id: String) = Post(
        id,
        "0",
        "user-$id",
        "User $id",
        "image.png",
        Date(),
        null,
        null,
        null,
        null,
        null,
        "Message $id",
        0,
    )

    private fun topic(posts: List<Post>) = Topic(
        "cat-1",
        "Category",
        "Subject",
        false,
        posts.size,
        10,
        Date(),
        Date(),
        posts,
    )

    private fun fullPage(prefix: String) = (0 until 10).map { post("$prefix-$it") }

    @Before
    fun setup() {
        // TopicViewModel's dataSingle hops onto Schedulers.computation() while mapping posts;
        // force that scheduler onto the trampoline too so load() completes synchronously.
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        // Post#toParsedPost() runs every post message through BBParser, whose TextPrototype
        // always calls code.toSpannableStringBuilder().linkify() (via LinkifyCompat), which
        // touches the real android.text.SpannableStringBuilder. That class is an unmocked
        // Android SDK stub on the JVM unit test classpath and throws "not mocked" on first
        // use. Mock TextPrototype.construct() the same way BBParserTest does, to skip the
        // Android-dependent Linkify path while still producing a usable BBTree.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        every { api.forum.topic("topic-1") } returns topicEndpoint

        viewModel = TopicViewModel("topic-1", mockk<Resources>(relaxed = true))
    }

    @After
    fun teardown() {
        RxJavaPlugins.setComputationSchedulerHandler(null)
        unmockkObject(TextPrototype)
    }

    @Test
    fun `load sets data and populates metaData`() {
        val posts = fullPage("p0")
        val testTopic = topic(posts)
        topicEndpoint.stubPagingSuccess(testTopic)

        viewModel.load()

        assertEquals(posts.map { it.id }, viewModel.data.value?.map { it.id })
        assertEquals("Subject", viewModel.metaData.value?.subject)
        assertEquals("cat-1", viewModel.metaData.value?.categoryId)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        topicEndpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        topicEndpoint.stubPagingSuccess(topic(fullPage("p0")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        topicEndpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        topicEndpoint.stubPagingSuccess(topic(fullPage("p0")))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPosts = fullPage("p1")
        topicEndpoint.stubPagingSuccess(topic(secondPosts))
        viewModel.reload()

        assertEquals(secondPosts.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `second load appends new posts and dedups by id`() {
        val firstPage = fullPage("p0")
        topicEndpoint.stubPagingSuccess(topic(firstPage))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = listOf(post("p1-0"), post("p1-1"))
        topicEndpoint.stubPagingSuccess(topic(secondPage))
        viewModel.loadIfPossible()

        val expectedIds = firstPage.map { it.id } + secondPage.map { it.id }
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `refresh merges page-0 results with existing data, new items first`() {
        val original = fullPage("p0")
        topicEndpoint.stubPagingSuccess(topic(original))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(post("new-0")) + original.drop(1)
        topicEndpoint.stubPagingSuccess(topic(refreshed))
        viewModel.refresh()

        val expectedIds = refreshed.map { it.id } + listOf(original.first().id)
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `error during refresh with existing data sets refreshError, not error`() {
        topicEndpoint.stubPagingSuccess(topic(fullPage("p0")))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        topicEndpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
        assertEquals(10, viewModel.data.value?.size)
    }
}
