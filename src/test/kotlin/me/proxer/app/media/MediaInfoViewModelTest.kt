package me.proxer.app.media

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.EntryEndpoint
import me.proxer.library.api.info.MediaUserInfoEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.Entry
import me.proxer.library.entity.info.MediaUserInfo
import me.proxer.library.enums.Category
import me.proxer.library.enums.FskConstraint
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

class MediaInfoViewModelTest : KoinTest {

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

    private lateinit var viewModel: MediaInfoViewModel

    private fun createEntry(
        id: String = entryId,
        isAgeRestricted: Boolean = false,
        fskConstraints: Set<FskConstraint> = emptySet(),
    ) = Entry(
        id = id,
        name = "Test Anime",
        fskConstraints = fskConstraints,
        description = "A test description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 500,
        ratingAmount = 100,
        clicks = 10000,
        category = Category.ANIME,
        license = License.LICENSED,
        adaptionInfo = AdaptionInfo(id = "99999", name = "Adaption", medium = Medium.MANGASERIES),
        isAgeRestricted = isAgeRestricted,
        synonyms = emptyList(),
        languages = setOf(MediaLanguage.GERMAN_SUB),
        seasons = emptyList(),
        translatorGroups = emptyList(),
        industries = emptyList(),
        tags = emptyList(),
        genres = emptyList(),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        viewModel = MediaInfoViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val entry = createEntry()

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(entry)

        viewModel.load()

        assertEquals(entry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `load sets login-required error for age restricted entry when not logged in`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry(isAgeRestricted = true))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when logged in but not confirmed`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry(isAgeRestricted = true))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val firstEntry = createEntry()
        val secondEntry = createEntry(fskConstraints = setOf(FskConstraint.FSK_16))

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(firstEntry)
        viewModel.load()
        assertEquals(firstEntry, viewModel.data.value)

        entryEndpoint.stubSuccess(secondEntry)
        viewModel.reload()

        assertEquals(secondEntry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `userInfoData is populated after successful load when logged in`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val userInfoEndpoint = mockk<MediaUserInfoEndpoint>(relaxed = true)
        val entry = createEntry()
        val mediaUserInfo = MediaUserInfo(
            isNoted = true,
            isFinished = false,
            isCanceled = false,
            isTopTen = false,
            isSubscribed = false,
        )

        every { storageHelper.isLoggedIn } returns true
        every { api.info.entry(entryId) } returns entryEndpoint
        every { api.info.userInfo(entryId) } returns userInfoEndpoint
        entryEndpoint.stubSuccess(entry)
        userInfoEndpoint.stubSuccess(mediaUserInfo)

        viewModel.load()

        assertEquals(entry, viewModel.data.value)
        assertEquals(mediaUserInfo, viewModel.userInfoData.value)
    }
}
