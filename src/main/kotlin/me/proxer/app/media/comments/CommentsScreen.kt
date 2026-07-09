package me.proxer.app.media.comments

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.enums.CommentSortCriteria
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CommentsScreen(mediaId: String) {
    val viewModel = koinViewModel<CommentsViewModel> { parametersOf(mediaId, CommentSortCriteria.RATING) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    CommentsContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
    )
}

@Composable
private fun CommentsContent(
    data: List<ParsedComment>?,
    error: ErrorAction?,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    ContentScreen(
        isLoading = isLoading && data.isNullOrEmpty(),
        error = if (data.isNullOrEmpty()) error else null,
        onRetry = onRetry,
        isSwipeToRefreshEnabled = true,
        onRefresh = onRefresh,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(data ?: emptyList(), key = { it.id }) { comment ->
                CommentItem(comment = comment)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CommentItem(comment: ParsedComment) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (comment.overallRating > 0) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f".format(comment.overallRating / 2.0f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            text = comment.date.distanceInWordsToNow(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!comment.parsedContent.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view -> view.tree = comment.parsedContent },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommentsContentPreview() {
    ProxerTheme {
        CommentsContent(
            data = null,
            error = null,
            isLoading = true,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
        )
    }
}
