package me.proxer.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import me.proxer.app.bookmark.BookmarkViewModel
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.tv.episode.TvEpisodeActivity
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.extension.startActivity
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun TvBookmarksScreenContent(
    entries: List<Bookmark>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onBookmarkClick: (Bookmark) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLoginClick: () -> Unit = {},
    onAgeConfirmed: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val total = gridState.layoutInfo.totalItemsCount
            lastVisible != null && total > 0 && lastVisible.index >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            isLoading && entries.isNullOrEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error,
                        onLoginClick = onLoginClick,
                        onRetryClick = onRetry,
                        onAgeConfirmed = onAgeConfirmed,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries ?: emptyList()) { bookmark ->
                        TvBookmarkCard(
                            bookmark = bookmark,
                            onClick = { onBookmarkClick(bookmark) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (isLoading && entries?.isNotEmpty() == true) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvBookmarksScreen() {
    val viewModel: BookmarkViewModel =
        koinViewModel { parametersOf(null, Category.ANIME, false) }
    val preferenceHelper: PreferenceHelper = koinInject()
    val context = LocalContext.current
    val entries by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvBookmarksScreenContent(
        entries = entries,
        isLoading = isLoading ?: false,
        error = error,
        onBookmarkClick = { bookmark ->
            TvEpisodeActivity.navigateTo(context, bookmark.entryId, bookmark.name, 0)
        },
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        onLoginClick = { context.startActivity<TvLoginActivity>() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}

@Composable
private fun TvBookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(180.dp).height(270.dp),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(bookmark.entryId).toString(),
                contentDescription = bookmark.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
            ) {
                Text(
                    text = bookmark.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Ep ${bookmark.episode}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Empty")
@Composable
private fun TvBookmarksScreenContentEmptyPreview() {
    TvTheme {
        TvBookmarksScreenContent(
            entries = emptyList(),
            isLoading = false,
            error = null,
            onBookmarkClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvBookmarksScreenContentLoadingPreview() {
    TvTheme {
        TvBookmarksScreenContent(
            entries = emptyList(),
            isLoading = true,
            error = null,
            onBookmarkClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvBookmarkCardPreview() {
    TvTheme {
        TvBookmarkCard(
            bookmark = fakeBookmark(),
            onClick = {},
        )
    }
}
