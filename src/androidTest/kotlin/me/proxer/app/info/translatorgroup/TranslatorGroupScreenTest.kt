package me.proxer.app.info.translatorgroup

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
import me.proxer.library.api.info.TranslatorGroupEndpoint
import me.proxer.library.api.list.TranslatorGroupProjectListEndpoint
import me.proxer.library.entity.info.TranslatorGroup
import me.proxer.library.entity.list.TranslatorGroupProject
import me.proxer.library.enums.Country
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.ProjectState
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Twin of IndustryScreenTest. Two lazily-composed tabs, both endpoints stubbed in @Before, no toolbar-title
 * assertion. The one shape difference: TranslatorGroupProject's slot-6 arg is ProjectState, where
 * IndustryProject has IndustryType.
 */
@RunWith(AndroidJUnit4::class)
class TranslatorGroupScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val groupId = "654"

    private fun project(id: String, name: String) = TranslatorGroupProject(
        id,
        name,
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        ProjectState.ONGOING,
        MediaState.FINISHED,
        0,
        0,
    )

    private fun launchGroup() = ActivityScenario.launch<TranslatorGroupActivity>(
        TranslatorGroupActivity.getIntent(context, groupId),
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

        val infoEndpoint = mockk<TranslatorGroupEndpoint>(relaxed = true)

        every { api.info.translatorGroup(groupId) } returns infoEndpoint
        every { infoEndpoint.build() } returns mockProxerCall(
            TranslatorGroup(
                groupId,
                "Group Alpha",
                Country.GERMANY,
                "group.png",
                "https://proxer.me".toHttpUrl(),
                "Group Description Alpha",
                1000,
                12,
            ),
        )

        val projectsEndpoint = mockk<TranslatorGroupProjectListEndpoint>(relaxed = true)

        every { api.list.translatorGroupProjectList(groupId) } returns projectsEndpoint
        every { projectsEndpoint.includeHentai(any()) } returns projectsEndpoint
        every { projectsEndpoint.page(any()) } returns projectsEndpoint
        every { projectsEndpoint.limit(any()) } returns projectsEndpoint
        every { projectsEndpoint.build() } returns mockProxerCall(listOf(project("p0", "Project Alpha")))
    }

    @Test
    fun info_tab_renders_group_description() {
        launchGroup().use {
            awaitText("Group Description Alpha")
        }
    }

    @Test
    fun projects_tab_renders_project_name() {
        launchGroup().use {
            composeTestRule.switchToTab(context.getString(R.string.section_translator_group_projects))

            awaitText("Project Alpha")
        }
    }
}
