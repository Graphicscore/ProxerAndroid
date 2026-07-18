package me.proxer.app.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.profile.about.ProfileAboutScreen
import me.proxer.app.profile.comment.ProfileCommentScreen
import me.proxer.app.profile.history.HistoryScreen
import me.proxer.app.profile.info.ProfileInfoScreen
import me.proxer.app.profile.media.ProfileMediaListScreen
import me.proxer.app.profile.topten.TopTenScreen
import me.proxer.app.ui.compose.ProxerScrollableTabRow
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(userId: String?, username: String?, initialTab: Int = 0, onBack: () -> Unit) {
    val viewModel = koinViewModel<ProfileViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val displayName = data?.info?.username ?: username?.ifBlank { null } ?: userId ?: ""
    val resolvedUserId = data?.info?.id ?: userId
    val resolvedUsername = data?.info?.username ?: username

    val tabs = listOf(
        stringResource(R.string.section_profile_info),
        stringResource(R.string.section_profile_about),
        stringResource(R.string.section_top_ten),
        stringResource(R.string.section_user_media_list_anime),
        stringResource(R.string.section_user_media_list_manga),
        stringResource(R.string.section_user_comments),
        stringResource(R.string.section_user_history),
    )

    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
    val scope = rememberCoroutineScope()

    ProfileContent(
        displayName = displayName,
        tabs = tabs,
        selectedTab = pagerState.currentPage,
        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
        onBack = onBack,
    ) { _ ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ProfileInfoScreen(userId = resolvedUserId, username = resolvedUsername)

                1 -> ProfileAboutScreen(userId = resolvedUserId, username = resolvedUsername)

                2 -> TopTenScreen(userId = resolvedUserId, username = resolvedUsername)

                3 -> ProfileMediaListScreen(
                    userId = resolvedUserId,
                    username = resolvedUsername,
                    category = Category.ANIME,
                )

                4 -> ProfileMediaListScreen(
                    userId = resolvedUserId,
                    username = resolvedUsername,
                    category = Category.MANGA,
                )

                5 -> ProfileCommentScreen(userId = resolvedUserId, username = resolvedUsername)

                6 -> HistoryScreen(userId = resolvedUserId, username = resolvedUsername)

                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    displayName: String,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    tabContent: @Composable (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = { Text(displayName) },
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ProxerScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(label) },
                    )
                }
            }
            tabContent(selectedTab)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    ProxerTheme {
        ProfileContent(
            displayName = "Username",
            tabs = listOf("Info", "About", "Top Ten", "Anime", "Manga", "Comments", "History"),
            selectedTab = 0,
            onTabSelected = {},
            onBack = {},
        ) { page ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tab $page")
            }
        }
    }
}
