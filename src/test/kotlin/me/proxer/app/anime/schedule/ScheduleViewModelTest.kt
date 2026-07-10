package me.proxer.app.anime.schedule

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.media.CalendarEndpoint
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
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

class ScheduleViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val validators: Validators by inject()

    private val endpoint = mockk<CalendarEndpoint>(relaxed = true)

    private lateinit var viewModel: ScheduleViewModel

    private val now = Date()

    private val mondayLater = CalendarEntry(
        id = "1", entryId = "100", name = "Show A", episode = 1, episodeTitle = "Ep 1",
        date = Date(now.time + 3_600_000), timezone = "Europe/Berlin", industryId = "10", industryName = "Studio A",
        weekDay = CalendarDay.MONDAY, uploadDate = now, genres = emptySet(), ratingSum = 10, ratingAmount = 2,
    )

    private val mondayEarlier = CalendarEntry(
        id = "2", entryId = "101", name = "Show B", episode = 2, episodeTitle = "Ep 2",
        date = now, timezone = "Europe/Berlin", industryId = "11", industryName = "Studio B",
        weekDay = CalendarDay.MONDAY, uploadDate = now, genres = emptySet(), ratingSum = 20, ratingAmount = 4,
    )

    private val tuesdayEntry = CalendarEntry(
        id = "3", entryId = "102", name = "Show C", episode = 5, episodeTitle = "Ep 5",
        date = now, timezone = "Europe/Berlin", industryId = "12", industryName = "Studio C",
        weekDay = CalendarDay.TUESDAY, uploadDate = now, genres = emptySet(), ratingSum = 5, ratingAmount = 1,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        every { api.media.calendar() } returns endpoint

        viewModel = ScheduleViewModel()
    }

    @Test
    fun `load groups and sorts calendar entries by weekday and date`() {
        endpoint.stubSuccess(listOf(mondayLater, mondayEarlier, tuesdayEntry))

        viewModel.load()

        assertEquals(
            mapOf(
                CalendarDay.MONDAY to listOf(mondayEarlier, mondayLater),
                CalendarDay.TUESDAY to listOf(tuesdayEntry),
            ),
            viewModel.data.value,
        )
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(listOf(tuesdayEntry))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(listOf(mondayEarlier))
        viewModel.load()
        assertEquals(mapOf(CalendarDay.MONDAY to listOf(mondayEarlier)), viewModel.data.value)

        endpoint.stubSuccess(listOf(tuesdayEntry))
        viewModel.reload()

        assertEquals(mapOf(CalendarDay.TUESDAY to listOf(tuesdayEntry)), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `isLoginRequired is false so validateLogin is never called`() {
        endpoint.stubSuccess(listOf(tuesdayEntry))

        viewModel.load()

        verify(exactly = 0) { validators.validateLogin() }
        assertNotNull(viewModel.data.value)
    }
}
