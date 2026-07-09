package me.proxer.app.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.profile.about.ProfileAboutScreen
import me.proxer.app.profile.comment.ProfileCommentScreen
import me.proxer.app.profile.history.HistoryScreen
import me.proxer.app.profile.info.ProfileInfoScreen
import me.proxer.app.profile.media.ProfileMediaListScreen
import me.proxer.app.profile.topten.TopTenScreen
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(userId: String?, username: String?, onBack: () -> Unit) {
    val viewModel = koinViewModel<ProfileViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val displayName = data?.info?.username ?: username?.ifBlank { null } ?: userId ?: ""
    val resolvedUserId = data?.info?.id ?: userId
    val resolvedUsername = data?.info?.username ?: username

    val tabs = listOf(
        R.string.section_profile_info,
        R.string.section_profile_about,
        R.string.section_top_ten,
        R.string.section_user_media_list_anime,
        R.string.section_user_media_list_manga,
        R.string.section_user_comments,
        R.string.section_user_history,
    )

    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ProfileInfoScreen(userId = resolvedUserId, username = resolvedUsername)
                    1 -> ProfileAboutScreen(userId = resolvedUserId, username = resolvedUsername)
                    2 -> TopTenScreen(userId = resolvedUserId, username = resolvedUsername)
                    3 -> ProfileMediaListScreen(userId = resolvedUserId, username = resolvedUsername, category = Category.ANIME)
                    4 -> ProfileMediaListScreen(userId = resolvedUserId, username = resolvedUsername, category = Category.MANGA)
                    5 -> ProfileCommentScreen(userId = resolvedUserId, username = resolvedUsername)
                    6 -> HistoryScreen(userId = resolvedUserId, username = resolvedUsername)
                    else -> Unit
                }
            }
        }
    }
}
