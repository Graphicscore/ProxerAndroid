package me.proxer.app.chat.prv.create

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rubengees.rxbus.RxBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.Participant
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.chat.prv.sync.MessengerWorker
import me.proxer.app.exception.ChatException
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.CreateConferenceEndpoint
import me.proxer.library.api.messenger.CreateConferenceGroupEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant
import java.io.IOException

class CreateConferenceViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val bus: RxBus by inject()
    private val messengerDao: MessengerDao by inject()

    private lateinit var viewModel: CreateConferenceViewModel

    private val fixtureConference = LocalConference(
        id = 123L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        viewModel = CreateConferenceViewModel()
    }

    private fun stubChatEndpoint(): CreateConferenceEndpoint {
        val endpoint = mockk<CreateConferenceEndpoint>(relaxed = true)

        every { api.messenger.createConference(any(), any()) } returns endpoint

        return endpoint
    }

    private fun stubGroupEndpoint(): CreateConferenceGroupEndpoint {
        val endpoint = mockk<CreateConferenceGroupEndpoint>(relaxed = true)

        every { api.messenger.createConferenceGroup(any(), any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `createChat calls the correct endpoint and sets isLoading`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createChat("hello", Participant("bob"))

        verify { api.messenger.createConference("hello", "bob") }
        assertTrue(viewModel.isLoading.value == true)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `createGroup calls the correct endpoint with mapped participant usernames`() {
        val endpoint = stubGroupEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createGroup("topic", "hello", listOf(Participant("bob"), Participant("alice")))

        verify { api.messenger.createConferenceGroup("topic", "hello", listOf("bob", "alice")) }
        assertTrue(viewModel.isLoading.value == true)
    }

    @Test
    fun `successful creation completes once the synchronization bus event arrives`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")
        every { messengerDao.findConference(123L) } returns fixtureConference

        viewModel.createChat("hello", Participant("bob"))
        bus.post(MessengerWorker.SynchronizationEvent())

        assertEquals(fixtureConference, viewModel.result.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `endpoint failure sets error and stops loading immediately`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubError()

        viewModel.createChat("hello", Participant("bob"))

        assertNotNull(viewModel.error.value)
        assertNull(viewModel.result.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `synchronization error event sets error once a creation is pending`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createChat("hello", Participant("bob"))
        bus.post(MessengerErrorEvent(ChatException(IOException("sync failed"))))

        assertNotNull(viewModel.error.value)
        assertNull(viewModel.result.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `synchronization bus event is ignored when no creation is pending`() {
        bus.post(MessengerWorker.SynchronizationEvent())

        assertNull(viewModel.result.value)
        assertNull(viewModel.isLoading.value)
    }
}
