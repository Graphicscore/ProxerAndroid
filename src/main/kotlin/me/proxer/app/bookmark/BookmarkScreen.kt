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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils.ErrorAction
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
    val context = LocalContext.current

    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var filterAvailable by remember { mutableStateOf(false) }
    val dismissedIds = remember { mutableStateOf(emptySet<String>()) }
    val displayedData = (data ?: emptyList()).filterNot { it.id in dismissedIds.value }

    LaunchedEffect(Unit) { viewModel.load() }

    BookmarkContent(
        data = data,
        displayedData = displayedData,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = viewModel.itemDeletionError,
        undoData = viewModel.undoData,
        undoError = viewModel.undoError,
        refreshError = viewModel.refreshError,
        showFilterMenu = showFilterMenu,
        selectedCategory = selectedCategory,
        filterAvailable = filterAvailable,
        onOpenDrawer = onOpenDrawer,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        onShowFilterMenu = { showFilterMenu = it },
        onSelectCategory = { category ->
            selectedCategory = category
            viewModel.category = category
        },
        onSetFilterAvailable = { available ->
            filterAvailable = available
            viewModel.filterAvailable = available
        },
        onDeleteItem = { bookmark ->
            dismissedIds.value = dismissedIds.value + bookmark.id
            viewModel.addItemToDelete(bookmark)
        },
        onUndo = {
            dismissedIds.value = emptySet()
            viewModel.undo()
        },
        onDeletionFailed = { dismissedIds.value = emptySet() },
        onBookmarkClick = { bookmark ->
            val activity = context as? Activity
            activity?.let {
                when (bookmark.category) {
                    Category.ANIME -> AnimeActivity.navigateTo(
                        it,
                        bookmark.entryId,
                        bookmark.episode,
                        bookmark.language.toAnimeLanguage(),
                        bookmark.name,
                    )

                    Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                        it,
                        bookmark.entryId,
                        bookmark.episode,
                        bookmark.language.toGeneralLanguage(),
                        bookmark.chapterName,
                        bookmark.name,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkContent(
    data: List<Bookmark>?,
    displayedData: List<Bookmark>,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: LiveData<ErrorAction?>,
    undoData: LiveData<Unit?>,
    undoError: LiveData<ErrorAction?>,
    refreshError: LiveData<ErrorAction?>,
    showFilterMenu: Boolean,
    selectedCategory: Category?,
    filterAvailable: Boolean,
    onOpenDrawer: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onShowFilterMenu: (Boolean) -> Unit,
    onSelectCategory: (Category?) -> Unit,
    onSetFilterAvailable: (Boolean) -> Unit,
    onDeleteItem: (Bookmark) -> Unit,
    onUndo: () -> Unit,
    onDeletionFailed: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    // itemDeletionError/undoData/undoError are all ResettingMutableLiveData - each event (a failed
    // delete, a successful delete offering undo, a failed undo) is one-shot, not continuous state.
    // observeAsState()+LaunchedEffect(value) would silently miss every event after the first
    // structurally-equal one (Unit==Unit always; two identical ErrorActions from repeated offline
    // swipes), since Compose's default state-equality policy skips recomposition when the "new"
    // value equals the current one. ObserveLiveDataEvent bypasses that.
    ObserveLiveDataEvent(itemDeletionError) { err ->
        onDeletionFailed()
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_bookmark_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(undoData) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.fragment_bookmark_delete_message),
                actionLabel = context.getString(R.string.action_undo),
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    ObserveLiveDataEvent(undoError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_undo, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = { Text(stringResource(R.string.section_bookmarks)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_drawer))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { onShowFilterMenu(true) }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { onShowFilterMenu(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_all)) },
                                onClick = {
                                    onSelectCategory(null)
                                    onShowFilterMenu(false)
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
                                    onSelectCategory(Category.ANIME)
                                    onShowFilterMenu(false)
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
                                    onSelectCategory(Category.MANGA)
                                    onShowFilterMenu(false)
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
                                onClick = { onSetFilterAvailable(!filterAvailable) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(displayedData, key = { it.id }) { bookmark ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            onDeleteItem(bookmark)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    ) {
                        BookmarkItem(
                            bookmark = bookmark,
                            onClick = { onBookmarkClick(bookmark) },
                        )
                    }
                }
                if (isLoading && !data.isNullOrEmpty()) {
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

@Preview(showBackground = true)
@Composable
private fun BookmarkContentPreview() {
    ProxerTheme {
        BookmarkContent(
            data = null,
            displayedData = emptyList(),
            error = null,
            isLoading = true,
            itemDeletionError = MutableLiveData(null),
            undoData = MutableLiveData(null),
            undoError = MutableLiveData(null),
            refreshError = MutableLiveData(null),
            showFilterMenu = false,
            selectedCategory = null,
            filterAvailable = false,
            onOpenDrawer = {},
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onShowFilterMenu = {},
            onSelectCategory = {},
            onSetFilterAvailable = {},
            onDeleteItem = {},
            onUndo = {},
            onDeletionFailed = {},
            onBookmarkClick = {},
        )
    }
}
