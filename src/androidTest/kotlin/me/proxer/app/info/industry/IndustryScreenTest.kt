package me.proxer.app.info.industry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.switchToTab
import me.proxer.library.api.info.IndustryEndpoint
import me.proxer.library.api.list.IndustryProjectListEndpoint
import me.proxer.library.entity.info.Industry
import me.proxer.library.entity.list.IndustryProject
import me.proxer.library.enums.Country
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.IndustryType
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IndustryScreen hosts two lazily-composed tabs (Info, Projects). Both endpoints are stubbed in @Before
 * because animateScrollToPage transiently composes the neighbour page, and an unstubbed relaxed-mock endpoint
 * would ClassCastException and kill the process. The Info test asserts on launch; the Projects test switches
 * first. Toolbar title is never asserted -- it falls back to the `name` intent extra.
 */
@RunWith(AndroidJUnit4::class)
class IndustryScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val industryId = "321"

    // ratingSum/ratingAmount 0/0 suppresses the rating row so it can't collide with assertions.
    private fun project(id: String, name: String) = IndustryProject(
        id,
        name,
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        IndustryType.STUDIO,
        MediaState.FINISHED,
        0,
        0,
    )

    private fun launchIndustry() = ActivityScenario.launch<IndustryActivity>(
        IndustryActivity.getIntent(context, industryId),
    )

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val infoEndpoint = mockk<IndustryEndpoint>(relaxed = true)

        every { api.info.industry(industryId) } returns infoEndpoint
        every { infoEndpoint.build() } returns mockProxerCall(
            Industry(
                industryId,
                "Studio Alpha",
                IndustryType.STUDIO,
                Country.JAPAN,
                "https://proxer.me".toHttpUrl(),
                "Industry Description Alpha",
            ),
        )

        val projectsEndpoint = mockk<IndustryProjectListEndpoint>(relaxed = true)

        every { api.list.industryProjectList(industryId) } returns projectsEndpoint
        every { projectsEndpoint.includeHentai(any()) } returns projectsEndpoint
        every { projectsEndpoint.page(any()) } returns projectsEndpoint
        every { projectsEndpoint.limit(any()) } returns projectsEndpoint
        every { projectsEndpoint.build() } returns mockProxerCall(listOf(project("p0", "Project Alpha")))
    }

    @Test
    fun info_tab_renders_industry_description() {
        launchIndustry().use {
            // description is rendered as plain Compose Text (not the toolbar title, which is intent-extra).
            awaitText("Industry Description Alpha")
        }
    }

    @Test
    fun projects_tab_renders_project_name() {
        launchIndustry().use {
            composeTestRule.switchToTab(context.getString(R.string.section_industry_projects))

            awaitText("Project Alpha")
        }
    }
}
