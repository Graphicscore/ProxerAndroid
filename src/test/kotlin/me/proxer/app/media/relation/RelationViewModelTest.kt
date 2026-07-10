package me.proxer.app.media.relation

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
import me.proxer.library.api.info.RelationsEndpoint
import me.proxer.library.entity.info.Relation
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaLanguage
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

class RelationViewModelTest : KoinTest {

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

    private lateinit var viewModel: RelationViewModel

    private fun createRelation(id: String) = Relation(
        id = id,
        name = "Related Anime $id",
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
        languages = setOf(MediaLanguage.GERMAN_SUB),
        year = 2020,
        season = null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        viewModel = RelationViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val relations = listOf(createRelation("1"), createRelation("2"))

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(relations)

        viewModel.load()

        assertEquals(relations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load filters out the entry itself from relations`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val selfRelation = createRelation(entryId)
        val otherRelation = createRelation("2")

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(listOf(selfRelation, otherRelation))

        viewModel.load()

        assertEquals(listOf(otherRelation), viewModel.data.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(listOf(createRelation("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val firstRelations = listOf(createRelation("1"))
        val secondRelations = listOf(createRelation("2"), createRelation("3"))

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(firstRelations)
        viewModel.load()
        assertEquals(firstRelations, viewModel.data.value)

        endpoint.stubSuccess(secondRelations)
        viewModel.reload()

        assertEquals(secondRelations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
