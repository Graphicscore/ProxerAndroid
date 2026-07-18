package me.proxer.app.chat.pub.message

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.chat.pub.room.info.ChatRoomInfoActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.enums.ChatMessageAction
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatRoomId: String, chatRoomName: String, chatRoomIsReadOnly: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<ChatViewModel> { parametersOf(chatRoomId) }
    val reportViewModel = koinViewModel<ChatReportViewModel>()
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val reportError by reportViewModel.error.observeAsState()
    val reportIsLoading by reportViewModel.isLoading.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumePolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.pausePolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ChatScreenContent(
        messages = data,
        error = error,
        isLoading = isLoading,
        sendMessageError = viewModel.sendMessageError,
        refreshError = viewModel.refreshError,
        reportData = reportViewModel.data,
        reportError = reportError,
        reportIsLoading = reportIsLoading,
        chatRoomId = chatRoomId,
        chatRoomName = chatRoomName,
        chatRoomIsReadOnly = chatRoomIsReadOnly,
        myUserId = storageHelper.user?.id,
        isLoggedIn = storageHelper.isLoggedIn,
        onBack = onBack,
        onSend = { viewModel.sendMessage(it) },
        onReport = { messageId, reason -> reportViewModel.sendReport(messageId, reason) },
        onDraftUpdate = { viewModel.updateDraft(it) },
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    messages: List<ParsedChatMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    sendMessageError: LiveData<ErrorUtils.ErrorAction?>,
    refreshError: LiveData<ErrorUtils.ErrorAction?>,
    reportData: LiveData<Unit?>,
    reportError: ErrorUtils.ErrorAction?,
    reportIsLoading: Boolean?,
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean,
    myUserId: String?,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReport: (messageId: String, reason: String) -> Unit,
    onDraftUpdate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var messageText by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var reportTarget by remember { mutableStateOf<ParsedChatMessage?>(null) }
    var reportReason by remember { mutableStateOf("") }

    val inputEnabled = !chatRoomIsReadOnly && isLoggedIn && !messages.isNullOrEmpty()

    ObserveLiveDataEvent(sendMessageError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_chat_send_message, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(reportData) {
        reportTarget = null
        reportReason = ""
        selectedIds = emptySet()
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
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
                    onClick = { onReport(reportTarget!!.id, reportReason) },
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
                ProxerTopAppBar(
                    title = { Text(chatRoomName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                (context as? Activity)?.let { activity ->
                                    ChatRoomInfoActivity.navigateTo(activity, chatRoomId, chatRoomName)
                                }
                            },
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    },
                )
            } else {
                ProxerTopAppBar(
                    title = { Text(selectedIds.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                reportTarget = messages?.firstOrNull { it.id in selectedIds }
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
                    placeholder = {
                        Text(
                            stringResource(
                                when {
                                    messages.isNullOrEmpty() -> R.string.fragment_chat_loading_message
                                    chatRoomIsReadOnly -> R.string.fragment_chat_read_only_message
                                    !isLoggedIn -> R.string.fragment_chat_login_required_message
                                    else -> R.string.fragment_messenger_message
                                },
                            ),
                        )
                    },
                    enabled = inputEnabled,
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
                    enabled = inputEnabled && messageText.trim().isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && messages.isNullOrEmpty(),
            error = if (messages.isNullOrEmpty()) error else null,
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
                    if (message.action == ChatMessageAction.REMOVE_MESSAGE) return@items
                    ChatMessageItem(
                        message = message,
                        isOwnMessage = message.userId == myUserId,
                        isSelected = message.id in selectedIds,
                        onUsernameClick = {
                            ProfileActivity.navigateTo(activity, message.userId, message.username, message.image)
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
private fun ChatScreenContentPreview() {
    ProxerTheme {
        ChatScreenContent(
            messages = null,
            error = null,
            isLoading = true,
            sendMessageError = MutableLiveData(null),
            refreshError = MutableLiveData(null),
            reportData = MutableLiveData(null),
            reportError = null,
            reportIsLoading = null,
            chatRoomId = "1",
            chatRoomName = "General",
            chatRoomIsReadOnly = false,
            myUserId = null,
            isLoggedIn = false,
            onBack = {},
            onSend = {},
            onReport = { _, _ -> },
            onDraftUpdate = {},
            onRetry = {},
        )
    }
}

@Composable
private fun ChatMessageItem(
    message: ParsedChatMessage,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.image.isNotBlank()) {
                    AsyncImage(
                        model = ProxerUrls.userImage(message.image).toString(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp),
                    )
                }
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
            text = message.instant.atZone(org.threeten.bp.ZoneId.systemDefault()).toLocalDateTime()
                .distanceInWordsToNow(context),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
