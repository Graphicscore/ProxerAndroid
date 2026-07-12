package me.proxer.app.chat.prv.message

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.conference.info.ConferenceInfoActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.threeten.bp.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerScreen(conference: LocalConference, initialMessage: String? = null, onBack: () -> Unit) {
    val viewModel = koinViewModel<MessengerViewModel> { parametersOf(conference) }
    val reportViewModel = koinViewModel<MessengerReportViewModel>()
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val currentConference by viewModel.conference.observeAsState(initial = conference)
    val deleted by viewModel.deleted.observeAsState()
    val reportError by reportViewModel.error.observeAsState()
    val reportIsLoading by reportViewModel.isLoading.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    LaunchedEffect(deleted) {
        if (deleted != null) onBack()
    }

    MessengerContent(
        messages = data,
        error = error,
        isLoading = isLoading,
        conference = currentConference,
        myUserId = storageHelper.user?.id,
        draft = viewModel.draft,
        refreshError = viewModel.refreshError,
        reportData = reportViewModel.data,
        reportError = reportError,
        reportIsLoading = reportIsLoading,
        initialMessage = initialMessage,
        onBack = onBack,
        onSend = { viewModel.sendMessage(it) },
        onReport = { reason -> reportViewModel.sendReport(currentConference.id.toString(), reason) },
        onDraftUpdate = { viewModel.updateDraft(it) },
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerContent(
    messages: List<LocalMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    conference: LocalConference,
    myUserId: String?,
    draft: LiveData<String?>,
    refreshError: LiveData<ErrorUtils.ErrorAction?>,
    reportData: LiveData<Unit?>,
    reportError: ErrorUtils.ErrorAction?,
    reportIsLoading: Boolean?,
    initialMessage: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReport: (reason: String) -> Unit,
    onDraftUpdate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var messageText by rememberSaveable { mutableStateOf(initialMessage ?: "") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var reportTarget by remember { mutableStateOf<LocalConference?>(null) }
    var reportReason by remember { mutableStateOf("") }

    ObserveLiveDataEvent(draft) {
        if (messageText.isBlank()) messageText = it
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    // reportData is a ResettingMutableLiveData - a one-shot success event, not continuous state.
    // observeAsState()+LaunchedEffect(value) would silently miss every event after the first
    // structurally-equal one (Unit == Unit always), since Compose's default state-equality policy
    // skips recomposition when the "new" value equals the current one. Only dismiss the dialog on
    // confirmed success, not eagerly on click - mirrors ChatScreen.kt's report-dialog handling.
    ObserveLiveDataEvent(reportData) {
        reportTarget = null
        reportReason = ""
        selectedIds = emptySet()
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = {
                reportTarget = null
                reportReason = ""
            },
            title = { Text(stringResource(R.string.dialog_chat_report_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text(stringResource(R.string.dialog_chat_report_message_hint)) },
                        enabled = reportIsLoading != true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    reportError?.let { err ->
                        Text(
                            text = stringResource(err.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onReport(reportReason) },
                    enabled = reportIsLoading != true,
                ) {
                    Text(stringResource(R.string.dialog_chat_report_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    reportTarget = null
                    reportReason = ""
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = {
                        TextButton(
                            onClick = {
                                val activity = context as? Activity ?: return@TextButton
                                if (conference.isGroup) {
                                    ConferenceInfoActivity.navigateTo(activity, conference)
                                } else {
                                    ProfileActivity.navigateTo(
                                        activity,
                                        null,
                                        conference.topic,
                                        conference.image,
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = conference.topic,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(selectedIds.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                reportTarget = conference
                                reportReason = ""
                            },
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = stringResource(R.string.action_report),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        onDraftUpdate(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.fragment_messenger_message)) },
                    singleLine = false,
                    maxLines = 5,
                )
                IconButton(
                    onClick = {
                        val text = messageText.trim()
                        if (text.isNotBlank()) {
                            onSend(text)
                            messageText = ""
                        }
                    },
                    enabled = messageText.trim().isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && messages == null,
            error = if (messages == null) error else null,
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
            ) {
                items(messages ?: emptyList(), key = { it.id }) { message ->
                    val activity = context as? Activity ?: return@items
                    MessageItem(
                        message = message,
                        isOwnMessage = message.userId == myUserId,
                        isSelected = message.id in selectedIds,
                        onUsernameClick = {
                            ProfileActivity.navigateTo(activity, message.userId, message.username)
                        },
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = if (message.id in selectedIds) {
                                    selectedIds - message.id
                                } else {
                                    selectedIds + message.id
                                }
                            }
                        },
                        onLongClick = {
                            selectedIds = selectedIds + message.id
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessengerContentPreview() {
    val conf = LocalConference(
        id = 1L,
        topic = "Sample Chat",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = true,
        isRead = true,
        date = Instant.EPOCH,
        unreadMessageAmount = 0,
        lastReadMessageId = "",
        isFullyLoaded = false,
    )
    ProxerTheme {
        MessengerContent(
            messages = null,
            error = null,
            isLoading = true,
            conference = conf,
            myUserId = null,
            draft = MutableLiveData(null),
            refreshError = MutableLiveData(null),
            reportData = MutableLiveData(null),
            reportError = null,
            reportIsLoading = null,
            initialMessage = null,
            onBack = {},
            onSend = {},
            onReport = {},
            onDraftUpdate = {},
            onRetry = {},
        )
    }
}

@Composable
private fun MessageItem(
    message: LocalMessage,
    isOwnMessage: Boolean,
    isSelected: Boolean,
    onUsernameClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
    ) {
        if (!isOwnMessage) {
            TextButton(
                onClick = onUsernameClick,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = message.username,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view -> view.tree = message.styledMessage },
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            text = message.date.toLocalDateTime().distanceInWordsToNow(context),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
