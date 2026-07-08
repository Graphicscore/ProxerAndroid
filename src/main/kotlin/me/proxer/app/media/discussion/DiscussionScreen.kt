package me.proxer.app.media.discussion

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.forum.TopicActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.library.entity.info.ForumDiscussion
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DiscussionScreen(mediaId: String) {
    val viewModel = koinViewModel<DiscussionViewModel> { parametersOf(mediaId) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ContentScreen(
        isLoading = isLoading == true,
        error = error,
        onRetry = { viewModel.load() },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(data ?: emptyList()) { discussion ->
                DiscussionItem(discussion = discussion)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DiscussionItem(discussion: ForumDiscussion) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (activity != null) {
                    TopicActivity.navigateTo(activity, discussion.id, discussion.categoryId, discussion.subject)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = discussion.subject,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.fragment_discussion_meta_info,
                discussion.firstPostUsername,
                discussion.categoryName,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
