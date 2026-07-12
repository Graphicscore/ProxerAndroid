package me.proxer.app.profile.info

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.forum.TopicActivity
import me.proxer.app.profile.ProfileViewModel
import me.proxer.app.profile.ProfileViewModel.UserInfoWrapper
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.distanceInWordsToNow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val RANK_FORUM_ID = "207664"
private const val RANK_FORUM_CATEGORY_ID = "79"
private const val RANK_FORUM_TOPIC = "Rangpunkte und Ränge"

@Composable
fun ProfileInfoScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<ProfileViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ProfileInfoContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        onRetry = { viewModel.load() },
    )
}

@Composable
private fun ProfileInfoContent(data: UserInfoWrapper?, error: ErrorAction?, isLoading: Boolean, onRetry: () -> Unit) {
    ContentScreen(
        isLoading = isLoading,
        error = error,
        onRetry = onRetry,
    ) {
        if (data != null) {
            ProfileInfoBody(data = data)
        }
    }
}

@Composable
private fun ProfileInfoBody(data: UserInfoWrapper) {
    val context = LocalContext.current
    val activity = context as? Activity
    val userInfo = data.info

    val totalPoints = userInfo.animePoints + userInfo.mangaPoints + userInfo.uploadPoints +
        userInfo.forumPoints + userInfo.infoPoints + userInfo.miscPoints

    val rank = rankToString(totalPoints, context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fragment_profile_points),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_anime),
            value = userInfo.animePoints.toString(),
        )
        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_manga),
            value = userInfo.mangaPoints.toString(),
        )
        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_uploads),
            value = userInfo.uploadPoints.toString(),
        )
        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_forum),
            value = userInfo.forumPoints.toString(),
        )
        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_info),
            value = userInfo.infoPoints.toString(),
        )
        ProfilePointRow(
            label = stringResource(R.string.fragment_profile_points_miscellaneous),
            value = userInfo.miscPoints.toString(),
        )
        ProfilePointRow(label = stringResource(R.string.fragment_profile_points_total), value = totalPoints.toString())

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = rank,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (activity != null) {
                        Modifier.clickable {
                            TopicActivity.navigateTo(activity, RANK_FORUM_ID, RANK_FORUM_CATEGORY_ID, RANK_FORUM_TOPIC)
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 4.dp),
        )

        if (userInfo.status.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.fragment_profile_status),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = userInfo.status + " - " + userInfo.lastStatusChange.distanceInWordsToNow(context),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (data.watchedEpisodes != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val episodes = data.watchedEpisodes
            val minutes = episodes * 20
            val hours = minutes / 60f
            val days = hours / 24f

            Text(
                text = stringResource(R.string.fragment_profile_episode_counter),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ProfilePointRow(
                label = stringResource(R.string.fragment_profile_epsiode_counter_episodes),
                value = episodes.toString(),
            )
            ProfilePointRow(
                label = stringResource(R.string.fragment_profile_epsiode_counter_minutes),
                value = minutes.toString(),
            )
            ProfilePointRow(
                label = stringResource(R.string.fragment_profile_epsiode_counter_hours),
                value = "%.1f".format(hours),
            )
            ProfilePointRow(
                label = stringResource(R.string.fragment_profile_epsiode_counter_days),
                value = "%.1f".format(days),
            )
        }
    }
}

@Composable
private fun ProfilePointRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.4f),
        )
    }
}

private fun rankToString(points: Int, context: android.content.Context) = context.getString(
    when {
        points < 10 -> R.string.rank_10
        points < 100 -> R.string.rank_100
        points < 200 -> R.string.rank_200
        points < 500 -> R.string.rank_500
        points < 700 -> R.string.rank_700
        points < 1_000 -> R.string.rank_1000
        points < 1_500 -> R.string.rank_1500
        points < 2_000 -> R.string.rank_2000
        points < 3_000 -> R.string.rank_3000
        points < 4_000 -> R.string.rank_4000
        points < 6_000 -> R.string.rank_6000
        points < 8_000 -> R.string.rank_8000
        points < 10_000 -> R.string.rank_10000
        points < 11_000 -> R.string.rank_11000
        points < 12_000 -> R.string.rank_12000
        points < 14_000 -> R.string.rank_14000
        points < 16_000 -> R.string.rank_16000
        points < 18_000 -> R.string.rank_18000
        points < 20_000 -> R.string.rank_20000
        else -> R.string.rank_kami_sama
    },
)

@Preview(showBackground = true)
@Composable
private fun ProfileInfoContentPreview() {
    ProxerTheme {
        ProfileInfoContent(data = null, error = null, isLoading = true, onRetry = {})
    }
}
