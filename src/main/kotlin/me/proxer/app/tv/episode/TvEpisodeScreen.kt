package me.proxer.app.tv.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.proxer.app.media.episode.EpisodeRow
import me.proxer.app.media.episode.EpisodeViewModel
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.library.enums.AnimeLanguage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvEpisodeScreen(
    entryId: String,
    entryName: String,
    onEpisodeClick: (episode: Int, language: AnimeLanguage) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: EpisodeViewModel = koinViewModel { parametersOf(entryId) }
    val episodes by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back", color = Color.White) }
            Text(entryName, fontSize = 24.sp, color = Color.White)
        }

        when {
            isLoading == true && episodes.isNullOrEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            error != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load episodes", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.reload() }) { Text("Retry") }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodes ?: emptyList(), key = { it.number }) { episodeRow ->
                        TvEpisodeItem(
                            episodeRow = episodeRow,
                            onClick = {
                                val lang = episodeRow.languageHosterList
                                    .firstOrNull()?.first?.toAnimeLanguage()
                                    ?: AnimeLanguage.ENGLISH_SUB
                                onEpisodeClick(episodeRow.number, lang)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeItem(episodeRow: EpisodeRow, onClick: () -> Unit) {
    val isWatched = episodeRow.userProgress != null && episodeRow.userProgress >= episodeRow.number
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWatched) Color(0xFF1A2A1A) else Color(0xFF1A1A1A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Episode ${episodeRow.number}", color = Color.White, fontSize = 18.sp)
                episodeRow.title?.let { Text(it, color = Color.Gray, fontSize = 13.sp) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                episodeRow.languageHosterList.forEach { (lang, _) ->
                    Surface(
                        color = Color(0xFF333333),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = lang.name,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isWatched) Text("✓", color = Color(0xFF4CAF50), fontSize = 18.sp)
            }
        }
    }
}
