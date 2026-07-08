package me.proxer.app.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.parseAsHtml
import me.proxer.app.R
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.extension.ProxerNotification
import me.proxer.app.util.extension.distanceInWordsToNow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(onBack: () -> Unit = {}) {
    val viewModel = koinViewModel<NotificationViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val deletionError by viewModel.deletionError.observeAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val dismissedIds = remember { mutableStateOf(emptySet<String>()) }
    val displayedData = (data ?: emptyList()).filterNot { it.id in dismissedIds.value }

    LaunchedEffect(Unit) {
        viewModel.load()
        AccountNotifications.cancel(context)
    }
    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }
    LaunchedEffect(deletionError) {
        deletionError?.let { errorAction ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_notification_deletion, context.getString(errorAction.message)),
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            text = { Text(stringResource(R.string.dialog_notification_deletion_confirmation_content)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        dismissedIds.value = emptySet()
                        showDeleteAllDialog = false
                    },
                ) {
                    Text(stringResource(R.string.dialog_notification_deletion_confirmation_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_notifications)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (displayedData.isNotEmpty()) showDeleteAllDialog = true },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete_all),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(displayedData, key = { it.id }) { notification ->
                    val activity = context as? BaseActivity
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            dismissedIds.value = dismissedIds.value + notification.id
                            viewModel.addItemToDelete(notification)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.fragment_notification_delete_content_description),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    ) {
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                activity?.showPage(notification.contentLink, skipCheck = true)
                            },
                        )
                    }
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

@Composable
private fun NotificationItem(notification: ProxerNotification, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = notification.text.parseAsHtml().toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = notification.date.distanceInWordsToNow(context),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
