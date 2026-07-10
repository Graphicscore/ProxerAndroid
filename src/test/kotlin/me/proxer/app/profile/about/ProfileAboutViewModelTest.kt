package me.proxer.app.profile.about

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
import me.proxer.library.api.user.UserAboutEndpoint
import me.proxer.library.entity.user.UserAbout
import me.proxer.library.enums.Gender
import me.proxer.library.enums.RelationshipStatus
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

class ProfileAboutViewModelTest : KoinTest {

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

    private lateinit var viewModel: ProfileAboutViewModel

    private fun createUserAbout(city: String = "Berlin") = UserAbout(
        website = "https://example.com",
        occupation = "Tester",
        interests = "Anime",
        city = city,
        country = "Germany",
        about = "About me",
        facebook = "facebook.user",
        youtube = "youtube.user",
        chatango = "chatango.user",
        twitter = "twitter.user",
        skype = "skype.user",
        deviantart = "deviantart.user",
        birthday = "2000-01-01",
        gender = Gender.UNKNOWN,
        relationshipStatus = RelationshipStatus.UNKNOWN,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = ProfileAboutViewModel(profileUserId, profileUsername)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)
        val userAbout = createUserAbout()

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(userAbout)

        viewModel.load()

        assertEquals(userAbout, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(createUserAbout())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)
        val firstUserAbout = createUserAbout(city = "Berlin")
        val secondUserAbout = createUserAbout(city = "Munich")

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(firstUserAbout)
        viewModel.load()
        assertEquals(firstUserAbout, viewModel.data.value)

        endpoint.stubSuccess(secondUserAbout)
        viewModel.reload()

        assertEquals(secondUserAbout, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
