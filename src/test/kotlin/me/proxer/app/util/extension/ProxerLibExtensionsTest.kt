package me.proxer.app.util.extension

import me.proxer.app.R
import me.proxer.library.enums.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the resource-id only extensions in ProxerLibExtensions.
 *
 * Deliberately free of Koin and [me.proxer.app.util.ErrorUtils]: the latter binds StorageHelper /
 * PreferenceHelper via `by lazy` once per JVM and would poison sibling tests in the same fork.
 */
class ProxerLibExtensionsTest {

    @Test
    fun `german language maps to germany flag`() {
        assertEquals(R.drawable.ic_germany, Language.GERMAN.toAppDrawableRes())
    }

    @Test
    fun `english language maps to united states flag`() {
        assertEquals(R.drawable.ic_united_states, Language.ENGLISH.toAppDrawableRes())
    }

    @Test
    fun `other language maps to united nations flag`() {
        assertEquals(R.drawable.ic_united_nations, Language.OTHER.toAppDrawableRes())
    }

    @Test
    fun `every language resolves to a valid resource id`() {
        Language.values().forEach { language ->
            assertNotEquals("No drawable for $language", 0, language.toAppDrawableRes())
        }
    }
}
