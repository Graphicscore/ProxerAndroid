package me.proxer.app.chat.pub.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubNullableError
import me.proxer.app.base.stubNullableSuccess
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.ReportChatMessageEndpoint
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

class ChatReportViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: ChatReportViewModel

    @Before
    fun setup() {
        viewModel = ChatReportViewModel()
    }

    private fun stubReport(): ReportChatMessageEndpoint {
        val endpoint = mockk<ReportChatMessageEndpoint>(relaxed = true)

        every { api.chat.reportMessage(any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `sendReport sets data on success`() {
        val endpoint = stubReport()
        endpoint.stubNullableSuccess(Unit)

        viewModel.sendReport("77", "spam")

        assertEquals(Unit, viewModel.data.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport calls reportMessage with the given ids`() {
        val endpoint = stubReport()
        endpoint.stubNullableSuccess(Unit)

        viewModel.sendReport("77", "spam")

        verify { api.chat.reportMessage("77", "spam") }
    }

    @Test
    fun `sendReport sets error on failure`() {
        val endpoint = stubReport()
        endpoint.stubNullableError()

        viewModel.sendReport("77", "spam")

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.sendReport("77", "spam")

        verify(exactly = 0) { api.chat.reportMessage(any(), any()) }
    }
}
