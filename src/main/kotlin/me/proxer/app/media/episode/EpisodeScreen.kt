package me.proxer.app.media.episode

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EpisodeScreen(mediaId: String, mediaName: String? = null) {
    val viewModel = koinViewModel<EpisodeViewModel> { parametersOf(mediaId) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ContentScreen(
        isLoading = isLoading == true,
        error = error,
        onRetry = { viewModel.load() },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(data ?: emptyList()) { episode ->
                EpisodeItem(episode = episode, mediaId = mediaId, mediaName = mediaName)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeItem(episode: EpisodeRow, mediaId: String, mediaName: String?) {
    val context = LocalContext.current
    val activity = context as? Activity
    var expanded by remember(episode.number) { mutableStateOf(false) }
    val isWatched = (episode.userProgress ?: 0) >= episode.number

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = episode.title ?: episode.category.toEpisodeAppString(context, episode.number),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (isWatched) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }

        if (expanded) {
            episode.languageHosterList.forEach { (language, _) ->
                Text(
                    text = language.toAppString(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activity ?: return@clickable
                            when (episode.category) {
                                Category.ANIME -> AnimeActivity.navigateTo(
                                    activity,
                                    mediaId,
                                    episode.number,
                                    language.toAnimeLanguage(),
                                    mediaName,
                                    episode.episodeAmount,
                                )
                                Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                                    activity,
                                    mediaId,
                                    episode.number,
                                    language.toGeneralLanguage(),
                                    episode.title,
                                    mediaName,
                                    episode.episodeAmount,
                                )
                            }
                        }
                        .padding(top = 4.dp, start = 8.dp),
                )
            }
        }
    }
}
