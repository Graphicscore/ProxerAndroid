package me.proxer.app.chat.prv.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubNullableError
import me.proxer.app.base.stubNullableSuccess
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerDatabase
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.ReportConferenceEndpoint
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

class MessengerReportViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val messengerDao: MessengerDao by inject()
    private val messengerDatabase: MessengerDatabase by inject()

    private lateinit var viewModel: MessengerReportViewModel

    private fun fixtureConference(isGroup: Boolean) = LocalConference(
        id = 5L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = isGroup,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        // The mocked MessengerDatabase would not run the transaction body unless stubbed to do so.
        every { messengerDatabase.runInTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }

        viewModel = MessengerReportViewModel()
    }

    private fun stubReport(): ReportConferenceEndpoint {
        val endpoint = mockk<ReportConferenceEndpoint>(relaxed = true)

        every { api.messenger.report(any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `sendReport deletes local messages and conference for a non-group chat`() {
        val endpoint = stubReport()
        endpoint.stubNullableSuccess(Unit)
        every { messengerDao.getConference(5L) } returns fixtureConference(isGroup = false)

        viewModel.sendReport("5", "spam")

        assertEquals(Unit, viewModel.data.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)

        verify { messengerDao.deleteMessagesByConferenceId("5") }
        verify { messengerDao.deleteConferenceById("5") }
    }

    @Test
    fun `sendReport keeps local data for a group chat`() {
        val endpoint = stubReport()
        endpoint.stubNullableSuccess(Unit)
        every { messengerDao.getConference(5L) } returns fixtureConference(isGroup = true)

        viewModel.sendReport("5", "spam")

        assertEquals(Unit, viewModel.data.value)

        verify(exactly = 0) { messengerDao.deleteMessagesByConferenceId(any()) }
        verify(exactly = 0) { messengerDao.deleteConferenceById(any()) }
    }

    @Test
    fun `sendReport sets error on failure`() {
        val endpoint = stubReport()
        endpoint.stubNullableError()

        viewModel.sendReport("5", "spam")

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.sendReport("5", "spam")

        verify(exactly = 0) { api.messenger.report(any(), any()) }
    }
}
