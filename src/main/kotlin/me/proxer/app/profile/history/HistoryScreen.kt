package me.proxer.app.profile.history

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HistoryScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<HistoryViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    ContentScreen(
        isLoading = isLoading == true && data.isNullOrEmpty(),
        error = if (data.isNullOrEmpty()) error else null,
        onRetry = { viewModel.load() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(data ?: emptyList(), key = { it.id }) { entry ->
                HistoryCard(entry = entry)
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: LocalUserHistoryEntry) {
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        onClick = {
            if (activity != null) {
                when (entry.category) {
                    Category.ANIME -> AnimeActivity.navigateTo(
                        activity,
                        entry.entryId,
                        entry.episode,
                        entry.language.toAnimeLanguage(),
                        entry.name,
                    )
                    Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                        activity,
                        entry.entryId,
                        entry.episode,
                        entry.language.toGeneralLanguage(),
                        null,
                        entry.name,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(entry.entryId).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = entry.medium.toAppString(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Text(
                text = if (entry is LocalUserHistoryEntry.Ucp) {
                    context.getString(
                        when (entry.category) {
                            Category.ANIME -> me.proxer.app.R.string.fragment_history_entry_ucp_status_anime
                            else -> me.proxer.app.R.string.fragment_history_entry_ucp_status_manga
                        },
                        entry.episode,
                        entry.date.distanceInWordsToNow(context),
                    )
                } else {
                    context.getString(
                        when (entry.category) {
                            Category.ANIME -> me.proxer.app.R.string.fragment_history_entry_status_anime
                            else -> me.proxer.app.R.string.fragment_history_entry_status_manga
                        },
                        entry.episode,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
