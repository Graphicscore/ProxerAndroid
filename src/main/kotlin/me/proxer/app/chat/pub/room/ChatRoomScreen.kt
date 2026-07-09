package me.proxer.app.chat.pub.room

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.proxer.app.chat.pub.message.ChatActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.library.entity.chat.ChatRoom
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatRoomList() {
    val viewModel = koinViewModel<ChatRoomViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    ContentScreen(
        isLoading = isLoading == true && data.isNullOrEmpty(),
        error = if (data.isNullOrEmpty()) error else null,
        onRetry = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(data ?: emptyList()) { room ->
                val activity = context as? Activity ?: return@items
                ChatRoomItem(
                    room = room,
                    onClick = { ChatActivity.navigateTo(activity, room.id, room.name, room.isReadOnly) },
                )
            }
        }
    }
}

@Composable
private fun ChatRoomItem(room: ChatRoom, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleSmall,
            )
            if (room.topic.isNotBlank()) {
                Text(
                    text = room.topic.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
