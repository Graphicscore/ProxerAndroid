package me.proxer.app.settings.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen() {
    val viewModel = koinViewModel<ServerStatusViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ServerStatusContent(
        isLoading = isLoading == true,
        error = error,
        servers = data,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerStatusContent(
    isLoading: Boolean,
    error: ErrorAction?,
    servers: List<ServerStatus>?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.section_server_status)) })
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
        ) {
            val safeServers = servers ?: return@ContentScreen
            Column {
                val allOnline = safeServers.all { it.online }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            if (allOnline) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (allOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(
                                if (allOnline) R.string.fragment_server_status_overall_online
                                else R.string.fragment_server_status_overall_offline,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(safeServers) { server -> ServerStatusItem(server) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerStatusScreenPreview() {
    ProxerTheme {
        ServerStatusContent(
            isLoading = false,
            error = null,
            servers = listOf(
                ServerStatus("Server 1", 1, ServerType.MAIN, true),
                ServerStatus("Server 2", 2, ServerType.MAIN, false),
                ServerStatus("Manga Server 1", 1, ServerType.MANGA, true),
                ServerStatus("Stream Server 1", 1, ServerType.STREAM, true),
            ),
            onRetry = {},
            onRefresh = {},
        )
    }
}

@Composable
private fun ServerStatusItem(server: ServerStatus) {
    Card(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                when (server.type) {
                    ServerType.MAIN -> Icons.Default.Language
                    ServerType.MANGA -> Icons.Default.Book
                    ServerType.STREAM -> Icons.Default.Tv
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(server.name, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(
                if (server.online) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (server.online) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
