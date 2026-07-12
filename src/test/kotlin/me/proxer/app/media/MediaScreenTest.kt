package me.proxer.app.media

import me.proxer.app.R
import me.proxer.library.enums.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaScreenTest {

    @Test
    fun `episodeTabTitleRes returns chapters title for manga`() {
        assertEquals(R.string.category_manga_episodes_title, episodeTabTitleRes(Category.MANGA))
    }

    @Test
    fun `episodeTabTitleRes returns episodes title for anime`() {
        assertEquals(R.string.category_anime_episodes_title, episodeTabTitleRes(Category.ANIME))
    }

    @Test
    fun `episodeTabTitleRes falls back to episodes title for null category`() {
        assertEquals(R.string.category_anime_episodes_title, episodeTabTitleRes(null))
    }

    @Test
    fun `episodeTabTitleRes returns chapters title for novel`() {
        assertEquals(R.string.category_manga_episodes_title, episodeTabTitleRes(Category.NOVEL))
    }
}
