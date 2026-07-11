package me.proxer.app.chat.prv.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.rubengees.rxbus.RxBus
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.chat.prv.sync.MessengerWorker
import me.proxer.app.exception.ChatMessageException
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.enums.Device
import me.proxer.library.enums.MessageAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant

class MessengerViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val messengerDao: MessengerDao by inject()
    private val bus: RxBus by inject()

    private lateinit var messagesLiveData: MediatorLiveData<List<LocalMessage>>
    private lateinit var conferenceLiveData: MutableLiveData<LocalConference?>
    private lateinit var initialConference: LocalConference
    private lateinit var viewModel: MessengerViewModel

    private fun conference(id: Long, isFullyLoaded: Boolean = false) = LocalConference(
        id,
        "Topic $id",
        "",
        2,
        "",
        "",
        false,
        true,
        true,
        Instant.now(),
        0,
        "0",
        isFullyLoaded,
    )

    private fun message(id: Long, conferenceId: Long) = LocalMessage(
        id,
        conferenceId,
        "u1",
        "User",
        "Hello $id",
        MessageAction.NONE,
        Instant.now(),
        Device.MOBILE,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        // LocalMessage eagerly parses its text into a BB tree on construction, which otherwise calls
        // through to android.text.LinkifyCompat / SpannableStringBuilder - unavailable in plain JUnit
        // (no Robolectric). Stub it out the same way BBParserTest does.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        mockkObject(MessengerWorker.Companion)
        every { MessengerWorker.enqueueMessageLoad(any()) } just Runs
        every { MessengerWorker.enqueueSynchronization() } just Runs
        every { MessengerWorker.isRunning } returns false

        initialConference = conference(1L)
        messagesLiveData = MediatorLiveData()
        conferenceLiveData = MutableLiveData()

        every { messengerDao.getMessagesLiveDataForConference(1L) } returns messagesLiveData
        every { messengerDao.getConferenceLiveData(1L) } returns conferenceLiveData

        viewModel = MessengerViewModel(initialConference)

        // MessengerViewModel's data/conference are MediatorLiveData: they only forward
        // from their sources while "active" (i.e. observed). Attach permanent observers so
        // the addSource() wiring done in init actually delivers emissions during the test.
        viewModel.data.observeForever {}
        viewModel.conference.observeForever {}
    }

    @After
    fun teardown() {
        unmockkObject(MessengerWorker.Companion)
        unmockkObject(TextPrototype)
    }

    @Test
    fun `initial conference value is seeded from constructor`() {
        assertEquals(initialConference, viewModel.conference.value)
    }

    @Test
    fun `data populates when dao emits non-empty messages`() {
        val messages = listOf(message(1L, 1L), message(2L, 1L))

        messagesLiveData.value = messages

        assertEquals(messages, viewModel.data.value)
        assertEquals(false, viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `empty message emission triggers a message load when not fully loaded`() {
        messagesLiveData.value = emptyList()

        verify { MessengerWorker.enqueueMessageLoad(1L) }
    }

    @Test
    fun `empty message emission does not enqueue a load when the conference is fully loaded`() {
        conferenceLiveData.value = conference(1L, isFullyLoaded = true)

        messagesLiveData.value = emptyList()

        verify(exactly = 0) { MessengerWorker.enqueueMessageLoad(any()) }
    }

    @Test
    fun `conference update from dao is reflected`() {
        val updated = conference(1L, isFullyLoaded = true)

        conferenceLiveData.value = updated

        assertEquals(updated, viewModel.conference.value)
    }

    @Test
    fun `null conference from dao marks conference as deleted`() {
        conferenceLiveData.value = null

        assertNotNull(viewModel.deleted.value)
    }

    @Test
    fun `load on page 0 marks the conference as read`() {
        viewModel.load()

        verify { messengerDao.markConferenceAsRead(1L) }
    }

    @Test
    fun `load enqueues a message load once past the first page and not fully loaded`() {
        messagesLiveData.value = List(MessengerWorker.MESSAGES_ON_PAGE) { message(it.toLong() + 1, 1L) }

        viewModel.load()

        verify { MessengerWorker.enqueueMessageLoad(1L) }
    }

    @Test
    fun `loadDraft populates draft from storage`() {
        every { storageHelper.getMessageDraft("1") } returns "draft text"

        viewModel.loadDraft()

        assertEquals("draft text", viewModel.draft.value)
    }

    @Test
    fun `updateDraft stores non-blank text and deletes blank text`() {
        viewModel.updateDraft("hello")
        verify { storageHelper.putMessageDraft("1", "hello") }

        viewModel.updateDraft("   ")
        verify { storageHelper.deleteMessageDraft("1") }
    }

    @Test
    fun `sendMessage inserts a pending message and enqueues synchronization`() {
        val user = LocalUser("token", "u1", "User", "image.png")

        every { storageHelper.user } returns user
        every { messengerDao.insertMessageToSend(user, "hi", 1L) } returns message(-1L, 1L)

        viewModel.sendMessage("hi")

        verify { messengerDao.insertMessageToSend(user, "hi", 1L) }
        verify { MessengerWorker.enqueueSynchronization() }
    }

    @Test
    fun `chat message error event sets error and clears loading`() {
        bus.post(MessengerErrorEvent(ChatMessageException(RuntimeException("boom"))))

        assertNotNull(viewModel.error.value)
        assertEquals(false, viewModel.isLoading.value)
    }
}
