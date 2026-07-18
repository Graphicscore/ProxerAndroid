package me.proxer.app.forum

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(id: String, categoryId: String, subject: String?, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val resources = context.resources
    val viewModel = koinViewModel<TopicViewModel> { parametersOf(id, resources) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val metaData by viewModel.metaData.observeAsState()

    val displaySubject = metaData?.subject ?: subject

    LaunchedEffect(Unit) { viewModel.load() }

    TopicContent(
        data = data,
        error = error,
        isLoading = isLoading,
        displaySubject = displaySubject,
        categoryId = categoryId,
        topicId = id,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        refreshError = viewModel.refreshError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicContent(
    data: List<ParsedPost>?,
    error: ErrorUtils.ErrorAction?,
    refreshError: LiveData<ErrorUtils.ErrorAction?>,
    isLoading: Boolean?,
    displaySubject: String?,
    categoryId: String,
    topicId: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProxerTopAppBar(
                title = {
                    Text(
                        text = displaySubject ?: "",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                onBack = onBack,
                actions = {
                    val shareSubject = displaySubject
                    if (shareSubject != null) {
                        IconButton(
                            onClick = {
                                (context as? Activity)?.let { activity ->
                                    val url = ProxerUrls.forumWeb(categoryId, topicId).toString()
                                    ShareCompat
                                        .IntentBuilder(activity)
                                        .setText(activity.getString(R.string.share_topic, shareSubject, url))
                                        .setType("text/plain")
                                        .setChooserTitle(activity.getString(R.string.share_title))
                                        .startChooser()
                                }
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList(), key = { it.id }) { post ->
                    PostItem(post = post)
                    HorizontalDivider()
                }
                if (isLoading == true && !data.isNullOrEmpty()) {
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

@Preview(showBackground = true)
@Composable
private fun TopicContentPreview() {
    ProxerTheme {
        TopicContent(
            data = null,
            error = null,
            refreshError = MutableLiveData(null),
            isLoading = true,
            displaySubject = "Sample Topic",
            categoryId = "1",
            topicId = "42",
            onBack = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Composable
private fun PostItem(post: ParsedPost) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // User header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val activity = context as? Activity ?: return@clickable
                        ProfileActivity.navigateTo(activity, post.userId, post.username, post.image)
                    }
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.image.isNotBlank()) {
                    AsyncImage(
                        model = ProxerUrls.userImage(post.image).toString(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = post.username,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }

            // Post BBCode content
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view ->
                    view.userId = post.userId
                    view.tree = post.parsedMessage
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Signature
            post.signature?.let { sig ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                AndroidView(
                    factory = { ctx -> BBCodeView(ctx) },
                    update = { view ->
                        view.userId = post.userId
                        view.tree = sig
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Footer row: date + thank-you count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = post.date.distanceInWordsToNow(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = post.thankYouAmount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
