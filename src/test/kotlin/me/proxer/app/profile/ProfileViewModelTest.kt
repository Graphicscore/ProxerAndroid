package me.proxer.app.profile

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
import me.proxer.library.api.ucp.WatchedEpisodesEndpoint
import me.proxer.library.api.user.UserInfoEndpoint
import me.proxer.library.entity.user.UserInfo
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
import java.util.Date

class ProfileViewModelTest : KoinTest {

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

    private fun createUserInfo(status: String = "status") = UserInfo(
        id = profileUserId,
        username = profileUsername,
        image = "avatar.png",
        isTeamMember = false,
        isDonator = false,
        status = status,
        lastStatusChange = Date(0),
        uploadPoints = 10,
        forumPoints = 20,
        animePoints = 30,
        mangaPoints = 40,
        infoPoints = 5,
        miscPoints = 1,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns null
    }

    @Test
    fun `load sets data on success for another users profile`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val userInfo = createUserInfo()

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(userInfo)

        viewModel.load()

        assertEquals(userInfo, viewModel.data.value?.info)
        assertNull(viewModel.data.value?.watchedEpisodes)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load fetches watched episodes for own profile`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val watchedEpisodesEndpoint = mockk<WatchedEpisodesEndpoint>(relaxed = true)
        val userInfo = createUserInfo()

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        every { api.ucp.watchedEpisodes() } returns watchedEpisodesEndpoint
        userInfoEndpoint.stubSuccess(userInfo)
        watchedEpisodesEndpoint.stubSuccess(42)

        viewModel.load()

        assertEquals(userInfo, viewModel.data.value?.info)
        assertEquals(42, viewModel.data.value?.watchedEpisodes)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(createUserInfo())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val firstUserInfo = createUserInfo(status = "First")
        val secondUserInfo = createUserInfo(status = "Second")

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(firstUserInfo)
        viewModel.load()
        assertEquals(firstUserInfo, viewModel.data.value?.info)

        userInfoEndpoint.stubSuccess(secondUserInfo)
        viewModel.reload()

        assertEquals(secondUserInfo, viewModel.data.value?.info)
        assertNull(viewModel.error.value)
    }
}
