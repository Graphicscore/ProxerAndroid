package me.proxer.app.chat.prv.conference.info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.ConferenceInfoEndpoint
import me.proxer.library.entity.messenger.ConferenceInfo
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

class ConferenceInfoViewModelTest : KoinTest {

    private companion object {
        private const val CONFERENCE_ID = "777"
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

    private val endpoint = mockk<ConferenceInfoEndpoint>(relaxed = true)

    private lateinit var viewModel: ConferenceInfoViewModel

    private val firstConferenceInfo = ConferenceInfo(
        topic = "Friends",
        participantAmount = 2,
        firstMessageTime = Date(),
        lastMessageTime = Date(),
        leaderId = "1",
        participants = emptyList(),
    )

    private val secondConferenceInfo = firstConferenceInfo.copy(topic = "Updated Friends")

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.messenger.conferenceInfo(CONFERENCE_ID) } returns endpoint

        viewModel = ConferenceInfoViewModel(CONFERENCE_ID)
    }

    @Test
    fun `load sets data on success`() {
        endpoint.stubSuccess(firstConferenceInfo)

        viewModel.load()

        assertEquals(firstConferenceInfo, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(firstConferenceInfo)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(firstConferenceInfo)
        viewModel.load()
        assertEquals(firstConferenceInfo, viewModel.data.value)

        endpoint.stubSuccess(secondConferenceInfo)
        viewModel.reload()

        assertEquals(secondConferenceInfo, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
