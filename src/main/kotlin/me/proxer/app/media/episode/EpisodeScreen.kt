package me.proxer.app.media.episode

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.app.util.extension.toAppDrawableRes
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EpisodeScreen(mediaId: String, mediaName: String? = null) {
    val viewModel = koinViewModel<EpisodeViewModel> { parametersOf(mediaId) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    EpisodeContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        mediaId = mediaId,
        mediaName = mediaName,
        bookmarkResult = viewModel.bookmarkData,
        bookmarkError = viewModel.bookmarkError,
        onRetry = { viewModel.load() },
        onBookmark = { number, language, category -> viewModel.bookmark(number, language, category) },
    )
}

@Composable
internal fun EpisodeContent(
    data: List<EpisodeRow>?,
    error: ErrorAction?,
    isLoading: Boolean,
    mediaId: String,
    mediaName: String?,
    bookmarkResult: LiveData<Unit?>,
    bookmarkError: LiveData<ErrorAction?>,
    onRetry: () -> Unit,
    onBookmark: (Int, MediaLanguage, Category) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var bookmarkEpisode by remember { mutableStateOf<EpisodeRow?>(null) }
    val scope = rememberCoroutineScope()

    ObserveLiveDataEvent(bookmarkResult) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success)) }
    }

    ObserveLiveDataEvent(bookmarkError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_set_user_info, context.getString(err.message)),
            )
        }
    }

    val episodeToBookmark = bookmarkEpisode
    if (episodeToBookmark != null) {
        AlertDialog(
            onDismissRequest = { bookmarkEpisode = null },
            title = { Text(stringResource(R.string.fragment_episodes_bookmark_language_dialog_title)) },
            text = {
                Column {
                    episodeToBookmark.languageHosterList.forEach { (language, _) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBookmark(
                                        episodeToBookmark.number,
                                        language,
                                        episodeToBookmark.category,
                                    )
                                    bookmarkEpisode = null
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Image(
                                painter = painterResource(language.toGeneralLanguage().toAppDrawableRes()),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = language.toAppString(context),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bookmarkEpisode = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { episode ->
                    EpisodeItem(
                        episode = episode,
                        mediaId = mediaId,
                        mediaName = mediaName,
                        onLongClick = { bookmarkEpisode = episode },
                    )
                    HorizontalDivider()
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeItem(episode: EpisodeRow, mediaId: String, mediaName: String?, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var expanded by remember(episode.number) { mutableStateOf(false) }
    val isWatched = (episode.userProgress ?: 0) >= episode.number

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = onLongClick,
            )
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                episode.languageHosterList.forEach { (language, hosterImages) ->
                    LanguageButton(
                        language = language,
                        hosterImages = hosterImages,
                        onClick = {
                            if (activity != null) {
                                navigateToEpisode(activity, episode, language, mediaId, mediaName)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(language: MediaLanguage, hosterImages: List<String>?, onClick: () -> Unit) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Image(
            painter = painterResource(language.toGeneralLanguage().toAppDrawableRes()),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = language.toAppString(context),
            style = MaterialTheme.typography.labelLarge,
        )

        if (!hosterImages.isNullOrEmpty()) {
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .height(16.dp),
            )

            hosterImages.forEach { hosterImage ->
                AsyncImage(
                    model = ProxerUrls.hosterImage(hosterImage).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

private fun navigateToEpisode(
    activity: Activity,
    episode: EpisodeRow,
    language: MediaLanguage,
    mediaId: String,
    mediaName: String?,
) = when (episode.category) {
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

@Preview(showBackground = true)
@Composable
private fun EpisodeContentPreview() {
    ProxerTheme {
        EpisodeContent(
            data = null,
            error = null,
            isLoading = true,
            mediaId = "1",
            mediaName = null,
            bookmarkResult = MutableLiveData(null),
            bookmarkError = MutableLiveData(null),
            onRetry = {},
            onBookmark = { _, _, _ -> },
        )
    }
}
