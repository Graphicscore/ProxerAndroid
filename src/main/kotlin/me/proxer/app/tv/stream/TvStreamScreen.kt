package me.proxer.app.tv.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.proxer.app.anime.AnimeStream
import me.proxer.app.anime.AnimeViewModel
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.tv.TvErrorView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.androidUri
import me.proxer.app.util.extension.toast
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvStreamScreen(
    entryId: String,
    episode: Int,
    language: AnimeLanguage,
    entryName: String,
    onLoginClick: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: AnimeViewModel = koinViewModel { parametersOf(entryId, language, episode) }
    val streamInfo by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val resolutionResult by viewModel.resolutionResult.observeAsState()
    val resolutionError by viewModel.resolutionError.observeAsState()
    val context = LocalContext.current
    var resolvingStreamId by remember { mutableStateOf<String?>(null) }
    // ResettingMutableLiveData suppresses null re-delivery to Compose, so we capture the
    // ErrorAction in local state before the LiveData resets to null.
    var showResolutionError by remember { mutableStateOf(false) }
    var resolutionErrorAction by remember { mutableStateOf<ErrorUtils.ErrorAction?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(resolutionError) {
        if (resolutionError != null) {
            resolutionErrorAction = resolutionError
            showResolutionError = true
            resolvingStreamId = null
        }
    }

    LaunchedEffect(resolutionResult) {
        when (val result = resolutionResult) {
            is StreamResolutionResult.Video -> result.play(
                context, entryId, entryName, episode, language,
                ProxerUrls.entryImage(entryId).androidUri(), true
            )
            is StreamResolutionResult.Link -> {
                try {
                    context.startActivity(result.makeIntent())
                } catch (e: Exception) {
                    context.toast("No app found to open this link", Toast.LENGTH_SHORT)
                }
            }
            is StreamResolutionResult.App -> {
                try {
                    result.navigate(context)
                } catch (e: Exception) {
                    context.toast("No app found to handle this stream", Toast.LENGTH_SHORT)
                }
            }
            is StreamResolutionResult.Message -> Toast.makeText(
                context, result.message, Toast.LENGTH_LONG
            ).show()
            null -> Unit
        }
        if (resolutionResult != null) {
            showResolutionError = false
            resolvingStreamId = null
        }
    }

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
            OutlinedButton(onClick = onBack) {
                Text("← Back", color = Color.White)
            }
            Column {
                Text(entryName, fontSize = 20.sp, color = Color.White)
                Text("Episode $episode • ${language.name}", fontSize = 14.sp, color = Color.Gray)
            }
        }

        if (showResolutionError && resolutionErrorAction != null) {
            TvErrorView(
                error = resolutionErrorAction!!,
                onLoginClick = onLoginClick,
                onRetryClick = { showResolutionError = false }
            )
        }

        when {
            isLoading == true && streamInfo == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error!!,
                        onLoginClick = onLoginClick,
                        onRetryClick = { viewModel.reload() }
                    )
                }
            }
            else -> {
                val streams = streamInfo?.streams ?: emptyList()
                if (streams.isEmpty() && isLoading != true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No streams available", color = Color.Gray, fontSize = 18.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(streams, key = { it.id }) { stream ->
                            TvStreamItem(
                                stream = stream,
                                isResolving = resolvingStreamId == stream.id,
                                onClick = {
                                    resolvingStreamId = stream.id
                                    viewModel.resolve(stream)
                                }
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
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TvStreamItem(
    stream: AnimeStream,
    isResolving: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stream.hosterName, color = Color.White, fontSize = 16.sp)
                Text("by ${stream.uploaderName}", color = Color.Gray, fontSize = 13.sp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stream.isOfficial) TvBadge(label = "Official", background = Color(0xFF1B5E20))
                if (!stream.isSupported) TvBadge(label = "External", background = Color(0xFF5D1A1A))
                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                }
            }
        }
    }
}
