package me.proxer.app.media.recommendation

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
import me.proxer.library.api.info.RecommendationsEndpoint
import me.proxer.library.entity.info.Recommendation
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
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

class RecommendationViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: RecommendationViewModel

    private fun createRecommendation(id: String) = Recommendation(
        id = id,
        name = "Recommended Anime $id",
        genres = setOf("Action"),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.LICENSED,
        positiveVotes = 10,
        negativeVotes = 2,
        userVote = null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = RecommendationViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)
        val recommendations = listOf(createRecommendation("1"), createRecommendation("2"))

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(recommendations)

        viewModel.load()

        assertEquals(recommendations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(listOf(createRecommendation("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)
        val firstRecommendations = listOf(createRecommendation("1"))
        val secondRecommendations = listOf(createRecommendation("2"), createRecommendation("3"))

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(firstRecommendations)
        viewModel.load()
        assertEquals(firstRecommendations, viewModel.data.value)

        endpoint.stubSuccess(secondRecommendations)
        viewModel.reload()

        assertEquals(secondRecommendations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
