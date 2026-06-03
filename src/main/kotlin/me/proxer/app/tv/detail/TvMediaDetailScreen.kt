package me.proxer.app.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.proxer.app.media.MediaInfoViewModel
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvMediaDetailScreen(
    entryId: String,
    entryName: String,
    onWatchEpisodes: (episodeAmount: Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: MediaInfoViewModel = koinViewModel { parametersOf(entryId) }
    val entry by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AsyncImage(
            model = ProxerUrls.entryImage(entryId).toString(),
            contentDescription = entryName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back", color = Color.White) }

            when {
                isLoading == true && entry == null -> CircularProgressIndicator(color = Color.White)
                error != null -> {
                    Text("Error loading details", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
                else -> entry?.let { e ->
                    Text(e.name, fontSize = 28.sp, color = Color.White)
                    Text(
                        "Rating: ${"%.1f".format(e.rating.toDouble())}/10",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text("Episodes: ${e.episodeAmount}", color = Color.LightGray, fontSize = 14.sp)
                    if (e.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Synopsis", color = Color.White, fontSize = 18.sp)
                        Text(e.description, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onWatchEpisodes(e.episodeAmount) }) {
                        Text("Watch Episodes", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
