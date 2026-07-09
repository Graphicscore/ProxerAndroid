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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.proxer.app.R
import me.proxer.app.chat.prv.conference.ConferenceList
import me.proxer.app.chat.prv.create.CreateConferenceActivity
import me.proxer.app.chat.pub.room.ChatRoomList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContainerScreen(onOpenDrawer: () -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_chat)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(
                            onClick = {
                                val activity = context as? Activity ?: return@IconButton
                                CreateConferenceActivity.navigateTo(activity, false)
                            },
                        ) {
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
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.fragment_chat_container_public)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.fragment_chat_container_private)) },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    0 -> ChatRoomList()
                    1 -> ConferenceList()
                }
            }
        }
    }
}
