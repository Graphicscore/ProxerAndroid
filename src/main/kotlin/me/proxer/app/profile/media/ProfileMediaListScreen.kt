package me.proxer.app.profile.media

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toCategory
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileMediaListScreen(userId: String?, username: String?, category: Category) {
    val viewModel = koinViewModel<ProfileMediaListViewModel>(
        key = "profile_media_${category.name}",
    ) { parametersOf(userId, username, category, null) }

    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ProfileMediaListContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = viewModel.itemDeletionError,
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        onDelete = { viewModel.addItemToDelete(it) },
    )
}

@Composable
private fun ProfileMediaListContent(
    data: List<LocalUserMediaListEntry>?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: LiveData<ErrorAction?>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (LocalUserMediaListEntry) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    // itemDeletionError is a ResettingMutableLiveData - each failure is a one-shot event, not
    // continuous state. observeAsState()+LaunchedEffect(value) would silently miss every failure
    // after the first structurally-equal one, since Compose's default state-equality policy skips
    // recomposition when the "new" value equals the current one. A raw Observer bypasses that.
    DisposableEffect(lifecycleOwner, itemDeletionError) {
        val observer = Observer<ErrorAction?> { err ->
            if (err != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_profile_media_list_deletion, context.getString(err.message)),
                    )
                }
            }
        }
        itemDeletionError.observe(lifecycleOwner, observer)
        onDispose { itemDeletionError.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data ?: emptyList(), key = { it.id }) { entry ->
                    MediaListCard(
                        entry = entry,
                        onDelete = onDelete,
                    )
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
private fun MediaListCard(entry: LocalUserMediaListEntry, onDelete: (LocalUserMediaListEntry) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        onClick = {
            if (activity != null) {
                MediaActivity.navigateTo(activity, entry.id, entry.name, entry.medium.toCategory())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(entry.id).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = entry.medium.toAppString(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = entry.mediaProgress.toEpisodeAppString(context, entry.episode, entry.medium.toCategory()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (entry is LocalUserMediaListEntry.Ucp) {
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileMediaListContentPreview() {
    ProxerTheme {
        ProfileMediaListContent(
            data = null,
            error = null,
            isLoading = true,
            itemDeletionError = MutableLiveData(null),
            onRetry = {},
            onLoadMore = {},
            onDelete = {},
        )
    }
}
