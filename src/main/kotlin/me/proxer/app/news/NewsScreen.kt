package me.proxer.app.news

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.forum.TopicActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.entity.notifications.NewsArticle
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<NewsViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    NewsContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        onOpenDrawer = onOpenDrawer,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        onArticleClick = { article ->
            val activity = context as? Activity
            activity?.let {
                TopicActivity.navigateTo(it, article.threadId, article.categoryId, article.subject)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsContent(
    data: List<NewsArticle>?,
    error: ErrorAction?,
    isLoading: Boolean,
    onOpenDrawer: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_news)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { article ->
                    NewsItem(
                        article = article,
                        onClick = { onArticleClick(article) },
                    )
                }
                if (isLoading && !data.isNullOrEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
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

@Composable
private fun NewsItem(article: NewsArticle, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ProxerUrls.newsImage(article.id, article.image).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                )
                Text(
                    text = article.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }
            Text(
                text = article.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = article.date.distanceInWordsToNow(context),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(4f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = article.category,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(6f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewsContentPreview() {
    ProxerTheme {
        NewsContent(
            data = null,
            error = null,
            isLoading = true,
            onOpenDrawer = {},
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onArticleClick = {},
        )
    }
}
