package me.proxer.app.media

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import me.proxer.library.enums.Category
import me.proxer.app.media.comments.CommentsScreen
import me.proxer.app.media.discussion.DiscussionScreen
import me.proxer.app.media.episode.EpisodeScreen
import me.proxer.app.media.info.MediaInfoScreen
import me.proxer.app.media.recommendation.RecommendationScreen
import me.proxer.app.media.relation.RelationScreen
import me.proxer.app.ui.compose.ProxerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun episodeTabTitleRes(category: Category?): Int = when (category) {
    Category.MANGA -> R.string.category_manga_episodes_title
    else -> R.string.category_anime_episodes_title
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(id: String, name: String, category: Category? = null, initialTab: Int = 0, onBack: () -> Unit) {
    val viewModel = koinViewModel<MediaInfoViewModel> { parametersOf(id) }
    val data by viewModel.data.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val displayName = data?.name ?: name.ifBlank { null } ?: id

    val tabs = listOf(
        R.string.section_media_info,
        R.string.section_comments,
        episodeTabTitleRes(category),
        R.string.section_relations,
        R.string.section_recommendations,
        R.string.section_discussions,
    )

    val tabLabels = tabs.map { stringResource(it) }
    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
    val scope = rememberCoroutineScope()

    MediaScreenContent(
        displayName = displayName,
        tabs = tabLabels,
        selectedTab = pagerState.currentPage,
        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
        onBack = onBack,
    ) { _ ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> MediaInfoScreen(id = id)
                1 -> CommentsScreen(mediaId = id)
                2 -> EpisodeScreen(mediaId = id, mediaName = data?.name ?: name)
                3 -> RelationScreen(mediaId = id)
                4 -> RecommendationScreen(mediaId = id)
                5 -> DiscussionScreen(mediaId = id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaScreenContent(
    displayName: String,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    tabContent: @Composable (Int) -> Unit,
) {
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
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
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
private fun MediaScreenPreview() {
    ProxerTheme {
        MediaScreenContent(
            displayName = "Steins;Gate",
            tabs = listOf("Info", "Comments", "Episodes"),
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
