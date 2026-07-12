package me.proxer.app.media.list

import me.proxer.library.enums.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaListScreenTest {

    @Test
    fun `mediaListViewModelKey differs between anime and manga`() {
        assertNotEquals(mediaListViewModelKey(Category.ANIME), mediaListViewModelKey(Category.MANGA))
    }

    @Test
    fun `mediaListViewModelKey is deterministic per category`() {
        assertEquals(mediaListViewModelKey(Category.ANIME), mediaListViewModelKey(Category.ANIME))
        assertEquals(mediaListViewModelKey(Category.MANGA), mediaListViewModelKey(Category.MANGA))
    }
}
