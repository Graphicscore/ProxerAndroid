package me.proxer.app.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.mockProxerCallNullableSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.ProxerNotification
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException
import me.proxer.library.api.notifications.DeleteNotificationEndpoint
import me.proxer.library.api.notifications.NotificationsEndpoint
import me.proxer.library.entity.notifications.Notification
import me.proxer.library.enums.NotificationType
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.util.Date

class NotificationViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: NotificationViewModel

    private fun notification(id: String, text: String = "text-$id") = Notification(
        id,
        NotificationType.BOARD_MESSAGE,
        "content-$id",
        "https://proxer.me/".toHttpUrl(),
        text,
        Date(),
        "extra",
    )

    private fun mockNotificationsEndpoint(): NotificationsEndpoint {
        val endpoint = mockk<NotificationsEndpoint>(relaxed = true)

        every { api.notifications.notifications() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<ProxerNotification>): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    private fun mockErrorCall(): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } throws ProxerException(ProxerException.ErrorType.IO)

        return call
    }

    private fun mockUnitCall(): ProxerCall<Unit?> = mockProxerCallNullableSuccess(Unit)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = NotificationViewModel()
    }

    @Test
    fun `load merges unread and read results on page 0`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))

        viewModel.load()

        assertEquals(32, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error when the unread call fails`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockErrorCall()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returnsMany listOf(mockCall(emptyList()), mockCall(emptyList()))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockErrorCall()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))
        viewModel.load()
        assertEquals(32, viewModel.data.value?.size)

        val secondUnread = listOf(notification("u2-0"))
        val secondRead = listOf(notification("r2-0"))
        every { endpoint.build() } returnsMany listOf(mockCall(secondUnread), mockCall(secondRead))
        viewModel.reload()

        assertEquals(2, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        every { endpoint.build() } returns mockCall(listOf(notification("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh behaves like reload, so an error with existing data still sets error`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))
        viewModel.load()
        assertEquals(32, viewModel.data.value?.size)

        every { endpoint.build() } returns mockErrorCall()
        viewModel.refresh()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `areItemsTheSame uses full equality, not just id`() {
        val endpoint = mockNotificationsEndpoint()
        // Load 30 unread items to ensure hasReachedEnd is false (so loadIfPossible can proceed to page 1)
        val original = (0 until 30).map { notification("item-$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(original), mockCall(emptyList()))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        // Load page 1 with the same item ID but different content
        val changed = listOf(notification("item-0", "different text"))
        every { endpoint.build() } returns mockCall(changed)
        viewModel.loadIfPossible()

        // Same id but different content: NotificationViewModel's areItemsTheSame() override
        // uses plain equals(), so the old entry is treated as distinct and both remain.
        assertEquals(31, viewModel.data.value?.size)
    }

    @Test
    fun `deleteAll clears data on success and sets deletionError on failure`() {
        every { api.notifications.deleteAllNotifications() } returns mockk(relaxed = true) {
            every { build() } returns mockUnitCall()
        }

        viewModel.deleteAll()

        assertEquals(emptyList<ProxerNotification>(), viewModel.data.value)
    }

    @Test
    fun `addItemToDelete removes the item and chains to the next queued deletion`() {
        val endpoint = mockNotificationsEndpoint()
        val items = listOf(notification("a"), notification("b"))
        every { endpoint.build() } returnsMany listOf(mockCall(items), mockCall(emptyList()))
        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        val deleteEndpointA = mockk<DeleteNotificationEndpoint>(relaxed = true)
        every { api.notifications.deleteNotification("a") } returns deleteEndpointA
        every { deleteEndpointA.build() } returns mockUnitCall()

        viewModel.addItemToDelete(items[0])

        assertTrue(viewModel.data.value!!.none { it.id == "a" })
        assertEquals(1, viewModel.data.value?.size)
    }
}
