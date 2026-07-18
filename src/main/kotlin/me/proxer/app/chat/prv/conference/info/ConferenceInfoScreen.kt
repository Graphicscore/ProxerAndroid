package me.proxer.app.chat.prv.conference.info

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.Utils
import me.proxer.app.util.extension.toLocalDateTimeBP
import me.proxer.library.entity.messenger.ConferenceInfo
import me.proxer.library.entity.messenger.ConferenceParticipant
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.threeten.bp.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceInfoScreen(conference: LocalConference, onBack: () -> Unit) {
    val viewModel = koinViewModel<ConferenceInfoViewModel> { parametersOf(conference.id.toString()) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    ConferenceInfoScreenContent(
        data = data,
        error = error,
        isLoading = isLoading,
        conference = conference,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onParticipantClick = { participant ->
            (context as? Activity)?.let { activity ->
                ProfileActivity.navigateTo(activity, participant.id, participant.username, participant.image)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceInfoScreenContent(
    data: ConferenceInfo?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    conference: LocalConference,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onParticipantClick: (ConferenceParticipant) -> Unit,
) {
    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = { Text(conference.topic) },
                onBack = onBack,
            )
        },
    ) { padding ->
        ContentScreen(
            // `isLoading != false`, not `== true`: isLoading is a MutableLiveData<Boolean?> with no initial
            // value (observeAsState above has no default), so on the first frame it is null -- before the
            // LaunchedEffect runs load(). With `== true` that null made ContentScreen fall through to content(),
            // which dereferences `data!!` while data is still null, crashing with an NPE. Treating null as
            // "still loading" shows the spinner until data or an error arrives.
            isLoading = isLoading != false && data == null,
            error = if (data == null) error else null,
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ConferenceInfoContent(
                info = data!!,
                onParticipantClick = onParticipantClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConferenceInfoScreenContentPreview() {
    val conf = LocalConference(
        id = 1L,
        topic = "Group Chat",
        customTopic = "",
        participantAmount = 3,
        image = "",
        imageType = "",
        isGroup = true,
        localIsRead = true,
        isRead = true,
        date = Instant.EPOCH,
        unreadMessageAmount = 0,
        lastReadMessageId = "",
        isFullyLoaded = false,
    )
    ProxerTheme {
        ConferenceInfoScreenContent(
            data = null,
            error = null,
            isLoading = true,
            conference = conf,
            onBack = {},
            onParticipantClick = {},
        )
    }
}

@Composable
private fun ConferenceInfoContent(info: ConferenceInfo, onParticipantClick: (ConferenceParticipant) -> Unit) {
    val dateTime = info.firstMessageTime.toLocalDateTimeBP()
    val creationDate = Utils.dateFormatter.format(dateTime)
    val creationTime = Utils.timeFormatter.format(dateTime)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.fragment_conference_info_time, creationDate, creationTime),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(info.participants) { participant ->
            ParticipantItem(
                participant = participant,
                isLeader = participant.id == info.leaderId,
                onClick = { onParticipantClick(participant) },
            )
        }
    }
}

@Composable
private fun ParticipantItem(participant: ConferenceParticipant, isLeader: Boolean, onClick: () -> Unit) {
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
            if (participant.image.isNotBlank()) {
                AsyncImage(
                    model = ProxerUrls.userImage(participant.image).toString(),
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
                    text = participant.username,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (participant.status.isNotBlank()) {
                    Text(
                        text = participant.status.trim(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (isLeader) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
