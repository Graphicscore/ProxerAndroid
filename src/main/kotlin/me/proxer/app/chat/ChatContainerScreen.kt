package me.proxer.app.chat

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.chat.prv.conference.ConferenceList
import me.proxer.app.chat.prv.create.CreateConferenceActivity
import me.proxer.app.chat.pub.room.ChatRoomList
import me.proxer.app.ui.compose.ProxerTabRow
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContainerScreen(onOpenDrawer: () -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val activity = context as? Activity

    ChatContainerContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onOpenDrawer = onOpenDrawer,
        onAddClick = { activity?.let { CreateConferenceActivity.navigateTo(it, false) } },
    ) {
        when (selectedTab) {
            0 -> ChatRoomList()
            1 -> ConferenceList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContainerContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onAddClick: () -> Unit,
    tabContent: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = { Text(stringResource(R.string.section_chat)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_drawer))
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_chat))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProxerTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text(stringResource(R.string.fragment_chat_container_public)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text(stringResource(R.string.fragment_chat_container_private)) },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                tabContent()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatContainerContentPreview() {
    ProxerTheme {
        ChatContainerContent(
            selectedTab = 0,
            onTabSelected = {},
            onOpenDrawer = {},
            onAddClick = {},
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
