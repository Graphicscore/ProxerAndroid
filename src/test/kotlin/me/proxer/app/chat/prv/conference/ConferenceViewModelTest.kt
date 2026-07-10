package me.proxer.app.chat.prv.conference

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.rubengees.rxbus.RxBus
import io.mockk.every
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.chat.prv.ConferenceWithMessage
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.exception.ChatSynchronizationException
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
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
import java.io.IOException

class ConferenceViewModelTest : KoinTest {

    private companion object {
        private const val SEARCH_QUERY = ""
    }

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

    private lateinit var conferencesLiveData: MutableLiveData<List<ConferenceWithMessage>>
    private lateinit var viewModel: ConferenceViewModel

    private val localConference = LocalConference(
        id = 1L,
        topic = "friend",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = false,
        isRead = false,
        date = Instant.now(),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    private val conferenceWithMessage = ConferenceWithMessage(localConference, null)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.areConferencesSynchronized } returns true

        conferencesLiveData = MutableLiveData()
        every { messengerDao.getConferencesLiveData(any()) } returns conferencesLiveData

        viewModel = ConferenceViewModel(SEARCH_QUERY)

        // `data` is a MediatorLiveData; its added sources are only actually subscribed to once it has an
        // active observer. Without this, setting values on the mocked source LiveData would be a no-op.
        viewModel.data.observeForever {}
    }

    @Test
    fun `source emission with data populates data and clears loading state`() {
        conferencesLiveData.value = listOf(conferenceWithMessage)

        assertEquals(listOf(conferenceWithMessage), viewModel.data.value)
        assertNull(viewModel.error.value)
        assertEquals(false, viewModel.isLoading.value)
    }

    @Test
    fun `empty source emission without synchronized conferences does not update data`() {
        every { storageHelper.areConferencesSynchronized } returns false

        conferencesLiveData.value = emptyList()

        assertNull(viewModel.data.value)
    }

    @Test
    fun `source emission is ignored when not logged in`() {
        every { storageHelper.isLoggedIn } returns false

        conferencesLiveData.value = listOf(conferenceWithMessage)

        assertNull(viewModel.data.value)
    }

    @Test
    fun `error event while loading clears data and sets error`() {
        viewModel.isLoading.value = true

        bus.post(MessengerErrorEvent(ChatSynchronizationException(IOException("sync failed"))))

        assertNull(viewModel.data.value)
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `error event is ignored when not loading`() {
        viewModel.isLoading.value = false

        bus.post(MessengerErrorEvent(ChatSynchronizationException(IOException("sync failed"))))

        assertNull(viewModel.error.value)
    }

    @Test
    fun `changing searchQuery switches the underlying source`() {
        val newLiveData = MutableLiveData<List<ConferenceWithMessage>>()
        every { messengerDao.getConferencesLiveData("new query") } returns newLiveData

        viewModel.searchQuery = "new query"
        verify { messengerDao.getConferencesLiveData("new query") }

        newLiveData.value = listOf(conferenceWithMessage)

        assertEquals(listOf(conferenceWithMessage), viewModel.data.value)
    }
}
