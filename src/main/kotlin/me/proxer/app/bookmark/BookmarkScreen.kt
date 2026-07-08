package me.proxer.app.bookmark

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<BookmarkViewModel> { parametersOf(null, null, false) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var filterAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_bookmarks)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_all)) },
                                onClick = {
                                    selectedCategory = null
                                    viewModel.category = null
                                    showFilterMenu = false
                                },
                                trailingIcon = if (selectedCategory == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_anime)) },
                                onClick = {
                                    selectedCategory = Category.ANIME
                                    viewModel.category = Category.ANIME
                                    showFilterMenu = false
                                },
                                trailingIcon = if (selectedCategory == Category.ANIME) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_manga)) },
                                onClick = {
                                    selectedCategory = Category.MANGA
                                    viewModel.category = Category.MANGA
                                    showFilterMenu = false
                                },
                                trailingIcon = if (selectedCategory == Category.MANGA) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_available)) },
                                onClick = {
                                    val newValue = !filterAvailable
                                    filterAvailable = newValue
                                    viewModel.filterAvailable = newValue
                                },
                                trailingIcon = if (filterAvailable) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { bookmark ->
                    val activity = context as? Activity ?: return@items
                    BookmarkItem(
                        bookmark = bookmark,
                        onClick = {
                            when (bookmark.category) {
                                Category.ANIME -> AnimeActivity.navigateTo(
                                    activity,
                                    bookmark.entryId,
                                    bookmark.episode,
                                    bookmark.language.toAnimeLanguage(),
                                    bookmark.name,
                                )
                                Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                                    activity,
                                    bookmark.entryId,
                                    bookmark.episode,
                                    bookmark.language.toGeneralLanguage(),
                                    bookmark.chapterName,
                                    bookmark.name,
                                )
                            }
                        },
                    )
                }
                if (isLoading == true && !data.isNullOrEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkItem(bookmark: Bookmark, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row {
            AsyncImage(
                model = ProxerUrls.entryImage(bookmark.entryId).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(0.8f),
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = bookmark.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = bookmark.medium.toAppString(context),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = bookmark.chapterName ?: bookmark.category.toEpisodeAppString(context, bookmark.episode),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(bookmark.language.toAppString(context)) },
                    )
                }
            }
        }
    }
}
