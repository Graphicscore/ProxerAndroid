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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import me.proxer.app.media.MediaInfoViewModel
import me.proxer.app.tv.TvErrorView
import me.proxer.app.tv.TvTheme
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun TvMediaDetailScreenContent(
    entryId: String,
    entryName: String,
    description: String?,
    rating: Float?,
    episodeAmount: Int?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onWatchEpisodes: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAgeConfirmed: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AsyncImage(
            model = ProxerUrls.entryImage(entryId).toString(),
            contentDescription = entryName,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }

            when {
                isLoading && description == null && error == null -> {
                    CircularProgressIndicator()
                }

                error != null -> {
                    TvErrorView(
                        error = error,
                        onRetryClick = onRetry,
                        onAgeConfirmed = onAgeConfirmed,
                    )
                }

                else -> {
                    if (description != null && rating != null && episodeAmount != null) {
                        Text(entryName, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Rating: ${"%.1f".format(rating.toDouble())}/10",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                        Text(
                            "Episodes: $episodeAmount",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                        if (description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Synopsis", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
                            Text(
                                description,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onWatchEpisodes(episodeAmount) }) {
                            Text("Watch Episodes", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvMediaDetailScreen(
    entryId: String,
    entryName: String,
    onWatchEpisodes: (episodeAmount: Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MediaInfoViewModel = koinViewModel { parametersOf(entryId) }
    val preferenceHelper: PreferenceHelper = koinInject()
    val entry by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    TvMediaDetailScreenContent(
        entryId = entryId,
        entryName = entryName,
        description = entry?.description,
        rating = entry?.rating,
        episodeAmount = entry?.episodeAmount,
        isLoading = isLoading ?: false,
        error = error,
        onWatchEpisodes = onWatchEpisodes,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvMediaDetailScreenContentLoadingPreview() {
    TvTheme {
        TvMediaDetailScreenContent(
            entryId = "1",
            entryName = "Attack on Titan",
            description = null,
            rating = null,
            episodeAmount = null,
            isLoading = true,
            error = null,
            onWatchEpisodes = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvMediaDetailScreenContentPopulatedPreview() {
    TvTheme {
        TvMediaDetailScreenContent(
            entryId = "1",
            entryName = "Attack on Titan",
            description = "In a world where humanity lives behind enormous walls, " +
                "gigantic humanoid Titans threaten those outside.",
            rating = 9.2f,
            episodeAmount = 25,
            isLoading = false,
            error = null,
            onWatchEpisodes = {},
            onBack = {},
            onRetry = {},
        )
    }
}
