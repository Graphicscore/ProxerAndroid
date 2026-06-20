package me.proxer.app.tv

import me.proxer.app.anime.AnimeStream
import me.proxer.app.media.episode.EpisodeRow
import me.proxer.library.entity.info.AnimeEpisode
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.CalendarDay
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.threeten.bp.Instant
import java.util.Date

internal fun fakeMediaListEntry(id: String = "1", name: String = "Attack on Titan") = MediaListEntry(
    id = id,
    name = name,
    genres = emptySet(),
    medium = Medium.ANIMESERIES,
    episodeAmount = 25,
    state = MediaState.FINISHED,
    ratingSum = 890,
    ratingAmount = 100,
    languages = setOf(MediaLanguage.ENGLISH_SUB),
)

internal fun fakeBookmark(
    id: String = "1",
    entryId: String = "1",
    name: String = "Attack on Titan",
    episode: Int = 5,
) = Bookmark(
    id = id,
    entryId = entryId,
    category = Category.ANIME,
    name = name,
    episode = episode,
    language = MediaLanguage.ENGLISH_SUB,
    medium = Medium.ANIMESERIES,
    state = MediaState.FINISHED,
    chapterName = null,
    isAvailable = true,
)

internal fun fakeCalendarEntry(
    id: String = "1",
    day: CalendarDay = CalendarDay.MONDAY,
    name: String = "Attack on Titan",
) = CalendarEntry(
    id = id,
    entryId = "1",
    name = name,
    episode = 5,
    episodeTitle = "The Fall of Shiganshina",
    date = Date(System.currentTimeMillis() + 3_600_000L),
    timezone = "UTC",
    industryId = "0",
    industryName = null,
    weekDay = day,
    uploadDate = Date(System.currentTimeMillis() + 7_200_000L),
    genres = emptySet(),
    ratingSum = 890,
    ratingAmount = 100,
)

internal fun fakeEpisodeRow(number: Int = 1, watched: Boolean = false) = EpisodeRow(
    category = Category.ANIME,
    userProgress = if (watched) number else null,
    episodeAmount = 24,
    episodes = listOf(
        AnimeEpisode(
            number = number,
            language = MediaLanguage.ENGLISH_SUB,
            hosters = emptySet(),
            hosterImages = emptyList(),
        ),
    ),
)

internal fun fakeAnimeStream(
    id: String = "1",
    hosterName: String = "Vidoza",
    isOfficial: Boolean = false,
    isSupported: Boolean = true,
) = AnimeStream(
    id = id,
    hoster = "vidoza",
    hosterName = hosterName,
    image = "",
    uploaderId = "1",
    uploaderName = "SubsPlease",
    date = Instant.now(),
    translatorGroupId = null,
    translatorGroupName = null,
    isOfficial = isOfficial,
    isPublic = true,
    isSupported = isSupported,
    resolutionResult = null,
)
