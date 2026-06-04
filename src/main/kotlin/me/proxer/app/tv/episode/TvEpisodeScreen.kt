package me.proxer.app.tv.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import me.proxer.app.media.episode.EpisodeRow
import me.proxer.app.media.episode.EpisodeViewModel
import me.proxer.app.tv.TvErrorView
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.library.enums.AnimeLanguage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.material3.Surface as M3Surface

@Composable
fun TvEpisodeScreen(
    entryId: String,
    entryName: String,
    onEpisodeClick: (episode: Int, language: AnimeLanguage) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: EpisodeViewModel = koinViewModel { parametersOf(entryId) }
    val episodes by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Text(entryName, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        when {
            isLoading == true && episodes.isNullOrEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error!!,
                        onRetryClick = { viewModel.reload() },
                    )
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(episodes ?: emptyList(), key = { it.number }) { episodeRow ->
                        TvEpisodeItem(
                            episodeRow = episodeRow,
                            onClick = {
                                val lang =
                                    episodeRow.languageHosterList
                                        .firstOrNull()
                                        ?.first
                                        ?.toAnimeLanguage()
                                        ?: AnimeLanguage.ENGLISH_SUB
                                onEpisodeClick(episodeRow.number, lang)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeItem(
    episodeRow: EpisodeRow,
    onClick: () -> Unit,
) {
    val isWatched = episodeRow.userProgress != null && episodeRow.userProgress >= episodeRow.number
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isWatched) {
                            MaterialTheme.colorScheme.surface.copy(green = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Episode ${episodeRow.number}", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                    episodeRow.title?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episodeRow.languageHosterList.forEach { (lang, _) ->
                        M3Surface(
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = lang.name,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (isWatched) Text("✓", color = Color(0xFF4CAF50), fontSize = 18.sp)
                }
            }
        }
    }
}
