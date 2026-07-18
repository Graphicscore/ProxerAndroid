package me.proxer.app.chat.pub.room.info

import android.app.Activity
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils
import me.proxer.library.entity.chat.ChatRoomUser
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomInfoScreen(chatRoomId: String, chatRoomName: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<ChatRoomInfoViewModel> { parametersOf(chatRoomId) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.load() }

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

    ChatRoomInfoContent(
        data = data,
        error = error,
        isLoading = isLoading,
        chatRoomName = chatRoomName,
        onBack = onBack,
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomInfoContent(
    data: List<ChatRoomUser>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    chatRoomName: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = { Text(chatRoomName) },
                onBack = onBack,
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data == null,
            error = if (data == null) error else null,
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { user ->
                    val activity = context as? Activity ?: return@items
                    ChatRoomUserItem(
                        user = user,
                        onClick = {
                            ProfileActivity.navigateTo(activity, user.id, user.name, user.image)
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomInfoContentPreview() {
    ProxerTheme {
        ChatRoomInfoContent(
            data = null,
            error = null,
            isLoading = true,
            chatRoomName = "General Chat",
            onBack = {},
            onRetry = {},
        )
    }
}

@Composable
private fun ChatRoomUserItem(user: ChatRoomUser, onClick: () -> Unit) {
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
            if (user.image.isNotBlank()) {
                AsyncImage(
                    model = ProxerUrls.userImage(user.image).toString(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (user.status.isNotBlank()) {
                    Text(
                        text = user.status.trim(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (user.isModerator) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
