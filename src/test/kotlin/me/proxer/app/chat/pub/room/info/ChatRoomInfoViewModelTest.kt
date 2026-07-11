package me.proxer.app.chat.pub.room.info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.ChatRoomUsersEndpoint
import me.proxer.library.entity.chat.ChatRoomUser
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

class ChatRoomInfoViewModelTest : KoinTest {

    private companion object {
        private const val CHAT_ROOM_ID = "555"
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

    private val endpoint = mockk<ChatRoomUsersEndpoint>(relaxed = true)

    private lateinit var viewModel: ChatRoomInfoViewModel

    private val moderator = ChatRoomUser(id = "1", name = "Zed", image = "", status = "online", isModerator = true)
    private val regular = ChatRoomUser(id = "2", name = "Amy", image = "", status = "offline", isModerator = false)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.chat.roomUsers(CHAT_ROOM_ID) } returns endpoint

        // The real dataSingle starts a repeating 10s poll (delaySubscription/repeatWhen/retryWhen, all on the
        // computation scheduler) after every successful load. Pinning computation to a manually-driven
        // TestScheduler (instead of RxTrampolineRule's trampoline, which honors real delays) keeps that pending
        // poll from ever firing during the test, since it is never advanced.
        RxJavaPlugins.setComputationSchedulerHandler { TestScheduler() }

        viewModel = ChatRoomInfoViewModel(CHAT_ROOM_ID)
    }

    @Test
    fun `load sets sorted data on success`() {
        endpoint.stubSuccess(listOf(regular, moderator))

        viewModel.load()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
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
        endpoint.stubSuccess(listOf(moderator))

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
        endpoint.stubSuccess(listOf(moderator))
        viewModel.load()
        assertEquals(listOf(moderator), viewModel.data.value)

        endpoint.stubSuccess(listOf(moderator, regular))
        viewModel.reload()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `pausePolling and resumePolling do not throw and preserve loaded data`() {
        endpoint.stubSuccess(listOf(moderator, regular))
        viewModel.load()

        viewModel.pausePolling()
        viewModel.resumePolling()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
    }
}
