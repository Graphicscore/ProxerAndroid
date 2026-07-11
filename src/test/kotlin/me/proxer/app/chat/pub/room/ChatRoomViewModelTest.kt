package me.proxer.app.chat.pub.room

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
import me.proxer.library.api.chat.PublicChatRoomsEndpoint
import me.proxer.library.api.chat.UserChatRoomsEndpoint
import me.proxer.library.entity.chat.ChatRoom
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

class ChatRoomViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val publicRoomsEndpoint = mockk<PublicChatRoomsEndpoint>(relaxed = true)
    private val userRoomsEndpoint = mockk<UserChatRoomsEndpoint>(relaxed = true)

    private lateinit var viewModel: ChatRoomViewModel

    private val roomOne = ChatRoom(id = "1", name = "General", topic = "Talk", isReadOnly = false)
    private val roomTwo = ChatRoom(id = "2", name = "Anime", topic = "Anime talk", isReadOnly = false)
    private val roomThree = ChatRoom(id = "3", name = "VIP", topic = "VIP talk", isReadOnly = true)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        every { api.chat.publicRooms() } returns publicRoomsEndpoint
        every { api.chat.userRooms() } returns userRoomsEndpoint

        viewModel = ChatRoomViewModel()
    }

    @Test
    fun `load sets data on success`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomTwo, roomOne))

        viewModel.load()

        assertEquals(listOf(roomOne, roomTwo), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        publicRoomsEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomOne))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        publicRoomsEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomOne))
        viewModel.load()
        assertEquals(listOf(roomOne), viewModel.data.value)

        publicRoomsEndpoint.stubSuccess(listOf(roomOne, roomTwo))
        viewModel.reload()

        assertEquals(listOf(roomOne, roomTwo), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load merges and deduplicates public and user rooms when logged in`() {
        every { storageHelper.isLoggedIn } returns true
        publicRoomsEndpoint.stubSuccess(listOf(roomOne, roomTwo))
        userRoomsEndpoint.stubSuccess(listOf(roomTwo, roomThree))

        viewModel.load()

        assertEquals(listOf(roomOne, roomTwo, roomThree), viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
