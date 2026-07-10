package me.proxer.app.profile.topten

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.DeleteFavoriteEndpoint
import me.proxer.library.api.ucp.UcpTopTenEndpoint
import me.proxer.library.api.user.UserTopTenEndpoint
import me.proxer.library.entity.ucp.UcpTopTenEntry
import me.proxer.library.entity.user.TopTenEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.Medium
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

class TopTenViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val profileUserId = "54321"
    private val profileUsername = "testuser"

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns null
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
    }

    @Test
    fun `load own profile populates data via ucp top ten`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)
        val entries = listOf(
            UcpTopTenEntry("a1", "e1", "Anime Entry", Medium.ANIMESERIES, Category.ANIME),
            UcpTopTenEntry("m1", "e2", "Manga Entry", Medium.MANGASERIES, Category.MANGA),
        )

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(entries)

        viewModel.load()

        assertEquals(1, viewModel.data.value?.animeEntries?.size)
        assertEquals(1, viewModel.data.value?.mangaEntries?.size)
        assertEquals("Anime Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertEquals("Manga Entry", viewModel.data.value?.mangaEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load other profile populates data via user top ten`() {
        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val baseEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val animeEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val mangaEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(profileUserId, profileUsername) } returns baseEndpoint
        every { baseEndpoint.includeHentai(any()) } returns baseEndpoint
        every { baseEndpoint.category(Category.ANIME) } returns animeEndpoint
        every { baseEndpoint.category(Category.MANGA) } returns mangaEndpoint
        animeEndpoint.stubSuccess(listOf(TopTenEntry("a1", "Anime Entry", Category.ANIME, Medium.ANIMESERIES)))
        mangaEndpoint.stubSuccess(listOf(TopTenEntry("m1", "Manga Entry", Category.MANGA, Medium.MANGASERIES)))

        viewModel.load()

        assertEquals(1, viewModel.data.value?.animeEntries?.size)
        assertEquals(1, viewModel.data.value?.mangaEntries?.size)
        assertEquals("Anime Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val baseEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val animeEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val mangaEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(profileUserId, profileUsername) } returns baseEndpoint
        every { baseEndpoint.includeHentai(any()) } returns baseEndpoint
        every { baseEndpoint.category(Category.ANIME) } returns animeEndpoint
        every { baseEndpoint.category(Category.MANGA) } returns mangaEndpoint
        animeEndpoint.stubError()
        mangaEndpoint.stubSuccess(listOf(TopTenEntry("m1", "Manga Entry", Category.MANGA, Medium.MANGASERIES)))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a1", "e1", "First Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.load()
        assertEquals("First Entry", viewModel.data.value?.animeEntries?.first()?.name)

        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a2", "e2", "Second Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.reload()

        assertEquals("Second Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `addItemToDelete removes the item from data on success`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)
        val deleteEndpoint = mockk<DeleteFavoriteEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a1", "e1", "Anime Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.load()
        assertEquals(1, viewModel.data.value?.animeEntries?.size)

        val itemToDelete = LocalTopTenEntry.Ucp("a1", "Anime Entry", Category.ANIME, Medium.ANIMESERIES, "e1")

        every { api.ucp.deleteFavorite("a1") } returns deleteEndpoint
        val deleteCall = mockk<me.proxer.library.ProxerCall<Unit?>>(relaxed = true)
        every { deleteCall.clone() } returns deleteCall
        every { deleteCall.safeExecute() } returns null
        every { deleteEndpoint.build() } returns deleteCall

        viewModel.addItemToDelete(itemToDelete)

        assertTrue(viewModel.data.value?.animeEntries.isNullOrEmpty())
        assertNull(viewModel.itemDeletionError.value)
    }
}
