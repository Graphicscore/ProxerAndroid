package me.proxer.app.media.comments

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.enums.CommentSortCriteria
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun CommentsScreen(mediaId: String) {
    val viewModel = koinViewModel<CommentsViewModel> { parametersOf(mediaId, CommentSortCriteria.RATING) }
    val storageHelper = koinInject<StorageHelper>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    CommentsContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = viewModel.itemDeletionError,
        refreshError = viewModel.refreshError,
        currentUserId = storageHelper.user?.id,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        onDelete = { viewModel.deleteComment(it) },
    )
}

@Composable
private fun CommentsContent(
    data: List<ParsedComment>?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: LiveData<ErrorAction?>,
    refreshError: LiveData<ErrorAction?>,
    currentUserId: String?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (ParsedComment) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ParsedComment?>(null) }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    ObserveLiveDataEvent(itemDeletionError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    deleteTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(R.string.dialog_comment_delete_message, comment.author)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(comment)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.dialog_comment_delete_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList(), key = { it.id }) { comment ->
                    CommentItem(
                        comment = comment,
                        isOwnComment = comment.authorId == currentUserId,
                        onDelete = { deleteTarget = comment },
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

@Composable
private fun CommentItem(comment: ParsedComment, isOwnComment: Boolean, onDelete: () -> Unit) {
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
            if (isOwnComment) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
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
            itemDeletionError = MutableLiveData(null),
            refreshError = MutableLiveData(null),
            currentUserId = null,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onDelete = {},
        )
    }
}
