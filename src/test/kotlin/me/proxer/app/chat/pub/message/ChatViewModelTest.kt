package me.proxer.app.chat.pub.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.ChatMessagesEndpoint
import me.proxer.library.api.chat.SendChatMessageEndpoint
import me.proxer.library.entity.chat.ChatMessage
import me.proxer.library.enums.ChatMessageAction
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

    private fun chatMessage(id: String) = ChatMessage(
        id,
        "user-$id",
        "User $id",
        "image.png",
        "Message $id",
        ChatMessageAction.NONE,
        Date(),
    )

    private fun fullPage(prefix: String) = (0 until 50).map { chatMessage("$prefix-$it") }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        messagesEndpoint = mockk<ChatMessagesEndpoint>(relaxed = true)
        every { api.chat.messages("room-1") } returns messagesEndpoint
        every { messagesEndpoint.messageId(any()) } returns messagesEndpoint

        viewModel = ChatViewModel("room-1")
    }

    @Test
    fun `load sets data on success`() {
        messagesEndpoint.stubSuccess(fullPage("p0"))

        viewModel.load()

        assertEquals(50, viewModel.data.value?.size)
        assertEquals("p0-0", viewModel.data.value?.first()?.id)
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
        messagesEndpoint.stubSuccess(fullPage("p0"))

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
        messagesEndpoint.stubSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        messagesEndpoint.stubSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }.toSet(), viewModel.data.value?.map { it.id }?.toSet())
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val shortPage = listOf(chatMessage("last-0"), chatMessage("last-1"))
        messagesEndpoint.stubSuccess(shortPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        messagesEndpoint.stubSuccess(listOf(chatMessage("should-not-appear")))

        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `an error with existing data always sets refreshError, regardless of page`() {
        messagesEndpoint.stubSuccess(fullPage("p0"))
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
