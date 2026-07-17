package me.proxer.app.settings.status

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.extension.safeInject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the only Group 1 screen that does not go through [me.proxer.library.ProxerApi]:
 * [ServerStatusViewModel] scrapes proxer.de with the raw [OkHttpClient] from Koin. Error/retry behaviour is
 * covered on the JVM by `ServerStatusViewModelTest`, so this only proves the harness can stub that path.
 */
@RunWith(AndroidJUnit4::class)
class ServerStatusScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    // InstrumentedTestBase exposes api/storageHelper/preferenceHelper/validators but not OkHttpClient, and
    // koin-test is declared testImplementation only (gradle/dependencies.gradle), so its inject() is not on the
    // androidTest classpath. Resolve from the running Koin instance directly instead.
    private val client: OkHttpClient by safeInject()

    // Shaped for the scraper: <td> elements with no <img> child, whose flattened child nodes zip pairwise into
    // (TextNode containing "server", Element whose text is exactly Online/Offline).
    private val onlineHtml = """
        <html><body><table>
        <tr><td>Server 1:</td><td><b>Online</b></td></tr>
        <tr><td>Server 2:</td><td><b>Offline</b></td></tr>
        </table></body></html>
    """.trimIndent()

    private fun buildResponse(html: String) = Response.Builder()
        .request(Request.Builder().url("https://proxer.de").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(html.toResponseBody("text/html".toMediaType()))
        .build()

    @Before
    fun setup() {
        // Required even though ServerStatusViewModel has isLoginRequired = false: BaseViewModel.init subscribes
        // to the login/age observables unconditionally, and BaseActivity.onCreate needs themeObservable.
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_scraped_server_name() {
        val call = mockk<Call>(relaxed = true)

        // CallStringBodySingle clones the call before executing it, so an unstubbed clone() would hand back a
        // different mock whose execute() is not stubbed.
        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        ActivityScenario.launch(ServerStatusActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Server 1").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Server 1").assertIsDisplayed()
        }
    }
}
