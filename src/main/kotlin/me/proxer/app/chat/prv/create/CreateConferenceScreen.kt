package me.proxer.app.chat.prv.create

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.chat.prv.Participant
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateConferenceScreen(isGroup: Boolean, initialParticipant: Participant? = null, onBack: () -> Unit) {
    val viewModel = koinViewModel<CreateConferenceViewModel>()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val context = LocalContext.current

    ObserveLiveDataEvent(viewModel.result) { conf ->
        val activity = context as? Activity ?: return@ObserveLiveDataEvent
        activity.finish()
        PrvMessengerActivity.navigateTo(activity, conf)
    }

    CreateConferenceContent(
        isGroup = isGroup,
        isLoading = isLoading,
        error = viewModel.error,
        initialParticipant = initialParticipant,
        onCreateChat = { message, participant -> viewModel.createChat(message, participant) },
        onCreateGroup = { topic, message, participants -> viewModel.createGroup(topic, message, participants) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateConferenceContent(
    isGroup: Boolean,
    isLoading: Boolean,
    error: LiveData<ErrorUtils.ErrorAction>,
    initialParticipant: Participant?,
    onCreateChat: (message: String, participant: Participant) -> Unit,
    onCreateGroup: (topic: String, message: String, participants: List<Participant>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var topic by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    var newParticipantName by remember { mutableStateOf("") }
    var showAddParticipant by remember { mutableStateOf(false) }
    val participants = remember {
        mutableStateListOf<Participant>().also { list ->
            initialParticipant?.let { list.add(it) }
        }
    }

    var topicError by remember { mutableStateOf<String?>(null) }
    var participantError by remember { mutableStateOf<String?>(null) }

    ObserveLiveDataEvent(error) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(it.message)) }
    }

    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isGroup) R.string.action_create_group else R.string.action_create_chat,
                        ),
                    )
                },
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (isGroup) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = {
                        topic = it
                        topicError = null
                    },
                    label = { Text(stringResource(R.string.fragment_create_conference_topic_hint)) },
                    isError = topicError != null,
                    supportingText = topicError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = stringResource(R.string.fragment_create_conference_participants),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(participants) { participant ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = participant.username,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isGroup || participants.size > 1) {
                            IconButton(onClick = { participants.remove(participant) }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                }
                if (isGroup || participants.isEmpty()) {
                    item {
                        if (!showAddParticipant) {
                            TextButton(onClick = { showAddParticipant = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(stringResource(R.string.fragment_create_conference_add_participant))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newParticipantName,
                                    onValueChange = {
                                        newParticipantName = it
                                        participantError = null
                                    },
                                    label = {
                                        Text(
                                            stringResource(R.string.fragment_create_conference_add_participant_hint),
                                        )
                                    },
                                    isError = participantError != null,
                                    supportingText = participantError?.let { { Text(it) } },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                IconButton(
                                    onClick = {
                                        val name = newParticipantName.trim()
                                        when {
                                            name.isBlank() -> participantError = context.getString(
                                                R.string.error_input_empty,
                                            )

                                            participants.any { it.username.equals(name, ignoreCase = true) } ->
                                                participantError = context.getString(
                                                    R.string.error_duplicate_participant,
                                                )

                                            else -> {
                                                participants.add(Participant(name))
                                                newParticipantName = ""
                                                if (!isGroup) showAddParticipant = false
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                                IconButton(onClick = {
                                    showAddParticipant = false
                                    newParticipantName = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = firstMessage,
                onValueChange = { firstMessage = it },
                label = { Text(stringResource(R.string.fragment_messenger_message)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val trimmedTopic = topic.trim()
                    val trimmedMessage = firstMessage.trim()
                    when {
                        isGroup && trimmedTopic.isBlank() -> {
                            topicError = context.getString(R.string.error_input_empty)
                        }

                        trimmedMessage.isBlank() -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.error_input_empty))
                            }
                        }

                        participants.isEmpty() -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.error_input_empty))
                            }
                        }

                        isGroup -> onCreateGroup(trimmedTopic, trimmedMessage, participants.toList())

                        else -> onCreateChat(trimmedMessage, participants.first())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoading != true,
            ) {
                if (isLoading == true) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(stringResource(if (isGroup) R.string.action_create_group else R.string.action_create_chat))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateConferenceContentPreview() {
    ProxerTheme {
        CreateConferenceContent(
            isGroup = false,
            isLoading = false,
            error = MutableLiveData(),
            initialParticipant = null,
            onCreateChat = { _, _ -> },
            onCreateGroup = { _, _, _ -> },
            onBack = {},
        )
    }
}
