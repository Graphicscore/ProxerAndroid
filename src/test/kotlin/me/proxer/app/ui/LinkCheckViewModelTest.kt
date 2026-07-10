package me.proxer.app.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.CheckLinkEndpoint
import me.proxer.library.entity.messenger.LinkCheckResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class LinkCheckViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: LinkCheckViewModel

    private val fixtureLink = "https://example.com".toHttpUrl()

    @Before
    fun setup() {
        viewModel = LinkCheckViewModel()
    }

    private fun stubCheck(): CheckLinkEndpoint {
        val endpoint = mockk<CheckLinkEndpoint>(relaxed = true)

        every { api.messenger.checkLink(any<HttpUrl>()) } returns endpoint

        return endpoint
    }

    @Test
    fun `check sets data to true for a secure link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        assertTrue(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check sets data to false for an insecure link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(false, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        assertFalse(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check falls back to false on failure`() {
        val endpoint = stubCheck()
        endpoint.stubError()

        viewModel.check(fixtureLink)

        assertFalse(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check calls the endpoint with the given link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        verify { api.messenger.checkLink(fixtureLink) }
    }

    @Test
    fun `a second check disposes the previous one and uses the new link`() {
        val firstEndpoint = mockk<CheckLinkEndpoint>(relaxed = true)
        val secondLink = "https://second.example.com".toHttpUrl()
        val secondEndpoint = mockk<CheckLinkEndpoint>(relaxed = true)

        every { api.messenger.checkLink(fixtureLink) } returns firstEndpoint
        every { api.messenger.checkLink(secondLink) } returns secondEndpoint
        firstEndpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))
        secondEndpoint.stubSuccess(LinkCheckResponse(false, secondLink.toString()))

        viewModel.check(fixtureLink)
        assertTrue(viewModel.data.value == true)

        viewModel.check(secondLink)
        assertFalse(viewModel.data.value == true)
    }
}
