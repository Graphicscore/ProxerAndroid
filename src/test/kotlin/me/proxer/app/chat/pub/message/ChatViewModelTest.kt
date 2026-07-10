package me.proxer.app.chat.pub.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.ChatMessagesEndpoint
import me.proxer.library.api.chat.SendChatMessageEndpoint
import me.proxer.library.entity.chat.ChatMessage
import me.proxer.library.enums.ChatMessageAction
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

class ChatViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var messagesEndpoint: ChatMessagesEndpoint
    private lateinit var viewModel: ChatViewModel
    private lateinit var computationScheduler: TestScheduler

    // ChatViewModel treats message ids as numeric (e.g. `id.toLong()` in findFirstRemoteId /
    // mergeNewDataWithExistingData / sendMessage), so fixtures must use numeric ids - unlike arbitrary
    // strings such as "p0-0", which blow up with NumberFormatException once real merge logic runs.
    private fun chatMessage(id: Long) = ChatMessage(
        id.toString(),
        "user-$id",
        "User $id",
        "image.png",
        "Message $id",
        ChatMessageAction.NONE,
        Date(),
    )

    private fun fullPage(base: Long) = (0 until 50).map { chatMessage(base + it) }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        // ParsedChatMessage eagerly parses its text into a BB tree on construction, which otherwise calls
        // through to android.text.LinkifyCompat / SpannableStringBuilder - unavailable in plain JUnit
        // (no Robolectric). Stub it out the same way BBParserTest does.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        messagesEndpoint = mockk<ChatMessagesEndpoint>(relaxed = true)
        every { api.chat.messages("room-1") } returns messagesEndpoint
        every { messagesEndpoint.messageId(any()) } returns messagesEndpoint

        // load()'s doOnSuccess unconditionally starts startPolling(), which repeatWhen/retryWhen/
        // delaySubscription on real 3s Flowable.timer() ticks (independent of RxTrampolineRule's
        // trampoline, which still honors real wall-clock delays) - left unpinned this repeats forever
        // and hangs every test that reaches a successful load(). Pin computation to a manually-driven
        // TestScheduler so the poll is scheduled but never fires unless a test explicitly advances it
        // (same pattern as ChatRoomInfoViewModelTest for the sibling ChatRoomInfoViewModel poll;
        // sendMessage()'s bounded RxRetryWithDelay(2, 3_000) retry needs one explicit advance to settle).
        computationScheduler = TestScheduler()
        RxJavaPlugins.setComputationSchedulerHandler { computationScheduler }

        viewModel = ChatViewModel("room-1")
    }

    @After
    fun teardown() {
        unmockkObject(TextPrototype)
    }

    @Test
    fun `load sets data on success`() {
        messagesEndpoint.stubSuccess(fullPage(0))

        viewModel.load()

        assertEquals(50, viewModel.data.value?.size)
        assertEquals("0", viewModel.data.value?.first()?.id)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure when there is no existing data`() {
        messagesEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        messagesEndpoint.stubSuccess(fullPage(0))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        messagesEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        messagesEndpoint.stubSuccess(fullPage(0))
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val secondPage = fullPage(1000)
        messagesEndpoint.stubSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }.toSet(), viewModel.data.value?.map { it.id }?.toSet())
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val shortPage = listOf(chatMessage(0), chatMessage(1))
        messagesEndpoint.stubSuccess(shortPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        messagesEndpoint.stubSuccess(listOf(chatMessage(999)))

        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `an error with existing data always sets refreshError, regardless of page`() {
        messagesEndpoint.stubSuccess(fullPage(0))
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        messagesEndpoint.stubError()

        viewModel.loadIfPossible()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sendMessage optimistically prepends the message and clears it on failure`() {
        every { storageHelper.user } returns me.proxer.app.auth.LocalUser("token", "self", "Self", "image.png")

        val sendEndpoint = mockk<SendChatMessageEndpoint>(relaxed = true)
        every { api.chat.sendMessage("room-1", "hi") } returns sendEndpoint
        sendEndpoint.stubError()

        viewModel.sendMessage("hi")

        // doSendMessages() retries once via RxRetryWithDelay(2, 3_000) before giving up - advance the
        // pinned computation TestScheduler past that single 3s delay so the retry (which also fails,
        // since every attempt is stubbed to error) can exhaust and propagate to sendMessageError.
        computationScheduler.advanceTimeBy(3, java.util.concurrent.TimeUnit.SECONDS)

        assertNotNull(viewModel.sendMessageError.value)
        assertEquals(true, viewModel.data.value?.isEmpty() != false)
    }

    @Test
    fun `loadDraft and updateDraft go through StorageHelper`() {
        every { storageHelper.getMessageDraft("room-1") } returns "draft text"

        viewModel.loadDraft()
        assertEquals("draft text", viewModel.draft.value)

        viewModel.updateDraft("new text")
        verify { storageHelper.putMessageDraft("room-1", "new text") }

        viewModel.updateDraft("")
        verify { storageHelper.deleteMessageDraft("room-1") }
    }
}
