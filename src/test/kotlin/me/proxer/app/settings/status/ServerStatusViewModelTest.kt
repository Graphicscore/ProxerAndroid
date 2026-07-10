package me.proxer.app.settings.status

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.io.IOException

class ServerStatusViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val client: OkHttpClient by inject()

    private lateinit var viewModel: ServerStatusViewModel

    private val onlineHtml = """
        <html><body><table>
        <tr><td>Server 1:</td><td><b>Online</b></td></tr>
        <tr><td>Server 2:</td><td><b>Offline</b></td></tr>
        </table></body></html>
    """.trimIndent()

    // NOTE: ServerStatusViewModel overrides isLoginRequired = false, so unlike every other
    // ViewModel in this suite, `storageHelper.isLoggedIn` is never consulted by validate() and
    // is deliberately left unstubbed here. Only the two init-block Observables are stubbed, since
    // BaseViewModel's init block subscribes to them unconditionally regardless of isLoginRequired.
    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        viewModel = ServerStatusViewModel()
    }

    private fun buildResponse(html: String) = Response.Builder()
        .request(Request.Builder().url("https://proxer.de").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(html.toResponseBody("text/html".toMediaType()))
        .build()

    @Test
    fun `load sets data on success`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertEquals(2, viewModel.data.value?.size)
        assertEquals("Server 1", viewModel.data.value?.get(0)?.name)
        assertTrue(viewModel.data.value?.get(0)?.online == true)
        assertEquals("Server 2", viewModel.data.value?.get(1)?.name)
        assertFalse(viewModel.data.value?.get(1)?.online == true)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { client.newCall(any()) } returns call
        every { call.execute() } returns buildResponse(onlineHtml)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        val secondHtml = """
            <html><body><table>
            <tr><td>Server 1:</td><td><b>Offline</b></td></tr>
            </table></body></html>
        """.trimIndent()

        every { call.execute() } returns buildResponse(secondHtml)
        viewModel.reload()

        assertEquals(1, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load succeeds without any login state stubbed since isLoginRequired is false`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertNotNull(viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
