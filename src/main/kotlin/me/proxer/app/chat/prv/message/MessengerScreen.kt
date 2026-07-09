package me.proxer.app.chat.prv.message

import android.app.Activity
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.conference.info.ConferenceInfoActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerScreen(
    conference: LocalConference,
    initialMessage: String? = null,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<MessengerViewModel> { parametersOf(conference) }
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val currentConference by viewModel.conference.observeAsState(initial = conference)
    val deleted by viewModel.deleted.observeAsState()
    val draft by viewModel.draft.observeAsState()
    val context = LocalContext.current
    var messageText by rememberSaveable { mutableStateOf(initialMessage ?: "") }

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    LaunchedEffect(draft) {
        if (draft != null && messageText.isBlank()) messageText = draft ?: ""
    }

    LaunchedEffect(deleted) {
        if (deleted != null) onBack()
    }

    val myUserId = storageHelper.user?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(
                        onClick = {
                            val activity = context as? Activity ?: return@TextButton
                            if (currentConference.isGroup) {
                                ConferenceInfoActivity.navigateTo(activity, currentConference)
                            } else {
                                ProfileActivity.navigateTo(
                                    activity,
                                    null,
                                    currentConference.topic,
                                    currentConference.image,
                                )
                            }
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = currentConference.topic,
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
                        viewModel.updateDraft(it)
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
                            viewModel.sendMessage(text)
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
            isLoading = isLoading == true && data == null,
            error = if (data == null) error else null,
            onRetry = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
            ) {
                items(data ?: emptyList(), key = { it.id }) { message ->
                    val activity = context as? Activity ?: return@items
                    MessageItem(
                        message = message,
                        isOwnMessage = message.userId == myUserId,
                        onUsernameClick = {
                            ProfileActivity.navigateTo(activity, message.userId, message.username)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: LocalMessage,
    isOwnMessage: Boolean,
    onUsernameClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = message.message,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = message.date.toLocalDateTime().distanceInWordsToNow(context),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
