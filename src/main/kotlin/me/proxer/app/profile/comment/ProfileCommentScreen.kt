package me.proxer.app.profile.comment

import android.app.Activity
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toEpisodeAppString
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileCommentScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<ProfileCommentViewModel> { parametersOf(userId, username, null) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    val listState = rememberLazyListState()

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 3) viewModel.loadIfPossible()
    }

    ContentScreen(
        isLoading = isLoading == true && data.isNullOrEmpty(),
        error = if (data.isNullOrEmpty()) error else null,
        onRetry = { viewModel.load() },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(data ?: emptyList(), key = { it.id }) { comment ->
                ProfileCommentItem(comment = comment)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProfileCommentItem(comment: ParsedUserComment) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = comment.entryName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        activity?.let {
                            MediaActivity.navigateTo(it, comment.entryId, comment.entryName, comment.category)
                        }
                    },
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

        Text(
            text = comment.mediaProgress.toEpisodeAppString(context, comment.episode, comment.category),
            style = MaterialTheme.typography.labelSmall,
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
