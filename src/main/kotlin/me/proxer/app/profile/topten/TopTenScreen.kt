package me.proxer.app.profile.topten

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
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
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TopTenScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<TopTenViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TopTenContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        onRetry = { viewModel.load() },
        onDelete = { viewModel.addItemToDelete(it) },
    )
}

@Composable
private fun TopTenContent(
    data: TopTenViewModel.ZippedTopTenResult?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    onRetry: () -> Unit,
    onDelete: (LocalTopTenEntry) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_top_ten_deletion, context.getString(err.message)),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
        ) {
            if (data != null) {
                TopTenBody(data = data, onDelete = onDelete)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopTenBody(data: TopTenViewModel.ZippedTopTenResult, onDelete: (LocalTopTenEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (data.animeEntries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.section_user_media_list_anime),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TopTenGrid(entries = data.animeEntries, onDelete = onDelete)
        }

        if (data.mangaEntries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.section_user_media_list_manga),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TopTenGrid(entries = data.mangaEntries, onDelete = onDelete)
        }
    }
}

@Composable
private fun TopTenGrid(entries: List<LocalTopTenEntry>, onDelete: (LocalTopTenEntry) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { entry ->
                    TopTenCard(
                        entry = entry,
                        onDelete = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TopTenCard(entry: LocalTopTenEntry, onDelete: (LocalTopTenEntry) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity

    val imageUrl = when (entry) {
        is LocalTopTenEntry.Ucp -> ProxerUrls.entryImage(entry.entryId).toString()
        else -> ProxerUrls.entryImage(entry.id).toString()
    }

    Card(
        onClick = {
            if (activity != null) {
                when (entry) {
                    is LocalTopTenEntry.Ucp -> MediaActivity.navigateTo(activity, entry.entryId, entry.name, entry.category)
                    else -> MediaActivity.navigateTo(activity, entry.id, entry.name, entry.category)
                }
            }
        },
        modifier = modifier,
    ) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
            if (entry is LocalTopTenEntry.Ucp) {
                TextButton(
                    onClick = { onDelete(entry) },
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dialog_comment_delete_positive),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopTenContentPreview() {
    ProxerTheme {
        TopTenContent(
            data = null,
            error = null,
            isLoading = true,
            itemDeletionError = null,
            onRetry = {},
            onDelete = {},
        )
    }
}
