package me.proxer.app.tv.stream

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import me.proxer.app.anime.AnimeStream
import me.proxer.app.anime.AnimeViewModel
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.fakeAnimeStream
import me.proxer.app.util.extension.androidUri
import me.proxer.app.util.extension.toast
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import androidx.compose.material3.Surface as M3Surface

@Composable
fun TvStreamScreen(entryId: String, episode: Int, language: AnimeLanguage, entryName: String, onBack: () -> Unit) {
    val viewModel: AnimeViewModel = koinViewModel { parametersOf(entryId, language, episode) }
    val streamInfo by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val resolutionResult by viewModel.resolutionResult.observeAsState()
    val resolutionError by viewModel.resolutionError.observeAsState()
    val context = LocalContext.current
    var resolvingStreamId by remember { mutableStateOf<String?>(null) }
    // ResettingMutableLiveData suppresses null re-delivery to Compose, so we use local state to
    // track transient error visibility rather than checking the LiveData value directly.
    var showResolutionError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadIfPossible() }

    LaunchedEffect(resolutionError) {
        if (resolutionError != null) {
            showResolutionError = true
            resolvingStreamId = null
        }
    }

    LaunchedEffect(resolutionResult) {
        when (val result = resolutionResult) {
            is StreamResolutionResult.Video -> {
                result.play(
                    context,
                    entryId,
                    entryName,
                    episode,
                    language,
                    ProxerUrls.entryImage(entryId).androidUri(),
                    true,
                )
            }

            is StreamResolutionResult.Link -> {
                try {
                    context.startActivity(result.makeIntent())
                } catch (e: Exception) {
                    Timber.w(e, "No app found to open stream link")
                    context.toast("No app found to open this link", Toast.LENGTH_SHORT)
                }
            }

            is StreamResolutionResult.App -> {
                try {
                    result.navigate(context)
                } catch (e: Exception) {
                    Timber.w(e, "No app found to handle stream")
                    context.toast("No app found to handle this stream", Toast.LENGTH_SHORT)
                }
            }

            is StreamResolutionResult.Message -> {
                Toast
                    .makeText(
                        context,
                        result.message,
                        Toast.LENGTH_LONG,
                    ).show()
            }

            null -> {}
        }
        if (resolutionResult != null) {
            showResolutionError = false
            resolvingStreamId = null
        }
    }

    TvStreamScreenContent(
        entryName = entryName,
        episode = episode,
        language = language,
        streams = streamInfo?.streams ?: emptyList(),
        isLoading = isLoading ?: false,
        hasError = error != null,
        showResolutionError = showResolutionError,
        resolvingStreamId = resolvingStreamId,
        onStreamClick = { stream ->
            resolvingStreamId = stream.id
            viewModel.resolve(stream)
        },
        onBack = onBack,
        onRetry = { viewModel.reload() },
    )
}

@Composable
fun TvStreamScreenContent(
    entryName: String,
    episode: Int,
    language: AnimeLanguage,
    streams: List<AnimeStream>,
    isLoading: Boolean,
    hasError: Boolean,
    showResolutionError: Boolean,
    resolvingStreamId: String?,
    onStreamClick: (AnimeStream) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
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
            OutlinedButton(onClick = onBack) {
                Text("← Back")
            }
            Column {
                Text(entryName, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Episode $episode • ${language.name}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        if (showResolutionError) {
            Text(
                "Resolution error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        when {
            isLoading && streams.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            hasError -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load streams", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }

            else -> {
                if (streams.isEmpty() && !isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No streams available",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(streams, key = { it.id }) { stream ->
                            TvStreamItem(
                                stream = stream,
                                isResolving = resolvingStreamId == stream.id,
                                onClick = { onStreamClick(stream) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvBadge(label: String, background: Color) {
    M3Surface(color = background, shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TvStreamItem(stream: AnimeStream, isResolving: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier =
        Modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stream.hosterName, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(
                    "by ${stream.uploaderName}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stream.isOfficial) TvBadge(label = "Official", background = Color(0xFF1B5E20))
                if (!stream.isSupported) TvBadge(label = "External", background = Color(0xFF5D1A1A))
                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvStreamScreenContentPreview() {
    TvTheme {
        TvStreamScreenContent(
            entryName = "Attack on Titan",
            episode = 3,
            language = AnimeLanguage.ENGLISH_SUB,
            streams = listOf(
                fakeAnimeStream(id = "1", hosterName = "Vidoza", isOfficial = true, isSupported = true),
                fakeAnimeStream(id = "2", hosterName = "Streamtape", isOfficial = false, isSupported = true),
                fakeAnimeStream(id = "3", hosterName = "ExternalSite", isOfficial = false, isSupported = false),
            ),
            isLoading = false,
            hasError = false,
            showResolutionError = false,
            resolvingStreamId = null,
            onStreamClick = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvStreamScreenContentLoadingPreview() {
    TvTheme {
        TvStreamScreenContent(
            entryName = "Attack on Titan",
            episode = 3,
            language = AnimeLanguage.ENGLISH_SUB,
            streams = emptyList(),
            isLoading = true,
            hasError = false,
            showResolutionError = false,
            resolvingStreamId = null,
            onStreamClick = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvStreamScreenContentErrorPreview() {
    TvTheme {
        TvStreamScreenContent(
            entryName = "Attack on Titan",
            episode = 3,
            language = AnimeLanguage.ENGLISH_SUB,
            streams = emptyList(),
            isLoading = false,
            hasError = true,
            showResolutionError = false,
            resolvingStreamId = null,
            onStreamClick = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvBadgePreview() {
    TvTheme {
        TvBadge(label = "Official", background = Color(0xFF1B5E20))
    }
}

@Preview(showBackground = true)
@Composable
private fun TvStreamItemPreview() {
    TvTheme {
        TvStreamItem(
            stream = fakeAnimeStream(id = "1", hosterName = "Vidoza", isOfficial = true, isSupported = true),
            isResolving = false,
            onClick = {},
        )
    }
}
