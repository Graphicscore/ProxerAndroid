package me.proxer.app.util.data

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the autoplay preference added for anime episode navigation.
 */
class PreferenceHelperTest {

    private val storedValues = mutableMapOf<String, Any>()

    private lateinit var preferenceHelper: PreferenceHelper

    @Before
    fun setUp() {
        storedValues.clear()

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)

        val key = slot<String>()
        val value = slot<Boolean>()

        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(capture(key), capture(value)) } answers {
            storedValues[key.captured] = value.captured

            editor
        }
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            storedValues[firstArg<String>()] as? Boolean ?: secondArg()
        }

        preferenceHelper = PreferenceHelper(
            initializer = mockk(relaxed = true),
            rxSharedPreferences = mockk(relaxed = true),
            sharedPreferences = sharedPreferences,
        )
    }

    @Test
    fun `autoplay next episode defaults to enabled`() {
        assertTrue(preferenceHelper.isAutoplayNextEpisodeEnabled)
    }

    @Test
    fun `autoplay next episode can be disabled`() {
        preferenceHelper.isAutoplayNextEpisodeEnabled = false

        assertFalse(preferenceHelper.isAutoplayNextEpisodeEnabled)
    }

    @Test
    fun `autoplay next episode can be re-enabled`() {
        preferenceHelper.isAutoplayNextEpisodeEnabled = false
        preferenceHelper.isAutoplayNextEpisodeEnabled = true

        assertTrue(preferenceHelper.isAutoplayNextEpisodeEnabled)
    }

    @Test
    fun `autoplay next episode is stored under the expected key`() {
        preferenceHelper.isAutoplayNextEpisodeEnabled = false

        assertEquals(false, storedValues[PreferenceHelper.AUTOPLAY_NEXT_EPISODE])
        assertEquals("autoplay_next_episode", PreferenceHelper.AUTOPLAY_NEXT_EPISODE)
    }

    @Test
    fun `autoplay next episode does not share storage with auto bookmark`() {
        preferenceHelper.isAutoplayNextEpisodeEnabled = false

        assertFalse(preferenceHelper.areBookmarksAutomatic)

        preferenceHelper.areBookmarksAutomatic = true

        assertFalse(preferenceHelper.isAutoplayNextEpisodeEnabled)
    }
}
