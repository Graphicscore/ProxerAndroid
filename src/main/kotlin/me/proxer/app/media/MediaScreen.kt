package me.proxer.app.media

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.comments.CommentsScreen
import me.proxer.app.media.discussion.DiscussionScreen
import me.proxer.app.media.episode.EpisodeScreen
import me.proxer.app.media.info.MediaInfoScreen
import me.proxer.app.media.recommendation.RecommendationScreen
import me.proxer.app.media.relation.RelationScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(id: String, name: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<MediaInfoViewModel> { parametersOf(id) }
    val data by viewModel.data.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val displayName = data?.name ?: name.ifBlank { null } ?: id

    val tabs = listOf(
        R.string.section_media_info,
        R.string.section_comments,
        R.string.category_anime_episodes_title,
        R.string.section_relations,
        R.string.section_recommendations,
        R.string.section_discussions,
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
}
