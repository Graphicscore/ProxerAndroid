package me.proxer.app.chat.prv.conference

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.chat.prv.ConferenceWithMessage
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toLocalDateTime
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Standalone conference list screen — used from PrvMessengerActivity in send-to mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceScreen(initialMessage: String? = null, onBack: () -> Unit) {
    val viewModel = koinViewModel<ConferenceViewModel> { parametersOf("") }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(searchQuery) { viewModel.searchQuery = searchQuery }

    Scaffold(
        topBar = {
            if (showSearch) {
                ProxerTopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                )
            } else {
                ProxerTopAppBar(
                    title = { Text(stringResource(R.string.activity_prv_messenger_send_to)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                )
            }
        },
    ) { padding ->
        ConferenceListContent(
            data = data,
            error = error,
            isLoading = isLoading,
            searchQuery = searchQuery,
            initialMessage = initialMessage,
            onRetry = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/** Embedded conference list — used inside ChatContainerScreen tab. */
@Composable
fun ConferenceList() {
    val viewModel = koinViewModel<ConferenceViewModel> { parametersOf("") }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    ConferenceListContent(
        data = data,
        error = error,
        isLoading = isLoading,
        searchQuery = "",
        initialMessage = null,
        onRetry = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ConferenceListContent(
    data: List<ConferenceWithMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    searchQuery: String,
    initialMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    ContentScreen(
        isLoading = isLoading == true && data == null,
        error = if (data == null || data.isEmpty()) error else null,
        onRetry = onRetry,
        modifier = modifier,
    ) {
        if (data != null && data.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        if (searchQuery.isBlank()) {
                            R.string.error_no_data_conferences
                        } else {
                            R.string.error_no_data_search
                        },
                    ),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { item ->
                    val activity = context as? Activity ?: return@items
                    ConferenceItem(
                        item = item,
                        onClick = { PrvMessengerActivity.navigateTo(activity, item.conference, initialMessage) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConferenceListContentPreview() {
    ProxerTheme {
        ConferenceListContent(
            data = null,
            error = null,
            isLoading = true,
            searchQuery = "",
            initialMessage = null,
            onRetry = {},
            modifier = Modifier,
        )
    }
}

@Composable
private fun ConferenceItem(item: ConferenceWithMessage, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.conference.image.isNotBlank()) {
                AsyncImage(
                    model = ProxerUrls.userImage(item.conference.image).toString(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    imageVector = if (item.conference.isGroup) Icons.Default.Group else Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.conference.topic,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.message != null) {
                    Text(
                        text = item.message.messageAction.toAppString(
                            context,
                            item.message.username,
                            item.message.messageText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.conference.date.toLocalDateTime().distanceInWordsToNow(context),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!item.conference.localIsRead) {
                    Badge {
                        Text(item.conference.unreadMessageAmount.toString())
                    }
                }
            }
        }
    }
}
