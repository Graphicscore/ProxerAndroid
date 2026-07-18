package me.proxer.app.media.list

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.LocalTag
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.enumSetOf
import me.proxer.app.util.extension.getQuantityString
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.Language
import me.proxer.library.enums.MediaSearchSortCriteria
import me.proxer.library.enums.MediaType
import me.proxer.library.enums.TagRateFilter
import me.proxer.library.enums.TagSpoilerFilter
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun mediaListViewModelKey(category: Category): String = "media_list_${category.name}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaListScreen(category: Category, onOpenDrawer: () -> Unit = {}) {
    val defaultType = if (category == Category.ANIME) MediaType.ALL_ANIME else MediaType.ALL_MANGA

    val viewModel = koinViewModel<MediaListViewModel>(
        key = mediaListViewModelKey(category),
    ) {
        parametersOf(
            MediaSearchSortCriteria.RATING,
            defaultType,
            null as String?,
            null as Language?,
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            enumSetOf<FskConstraint>(),
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            TagRateFilter.RATED_ONLY,
            TagSpoilerFilter.NO_SPOILERS,
            false as Boolean?,
        )
    }

    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val genreData by viewModel.genreData.observeAsState(emptyList())
    val tagData by viewModel.tagData.observeAsState(emptyList())

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Staged filter state — applied to viewModel when the "Search" button is tapped
    var pendingSortCriteria by remember { mutableStateOf(MediaSearchSortCriteria.RATING) }
    var pendingType by remember { mutableStateOf(defaultType) }
    var pendingLanguage by remember { mutableStateOf<Language?>(null) }
    var pendingGenres by remember { mutableStateOf<List<LocalTag>>(emptyList()) }
    var pendingExcludedGenres by remember { mutableStateOf<List<LocalTag>>(emptyList()) }
    var pendingFskConstraints by remember { mutableStateOf<Set<FskConstraint>>(emptySet()) }
    var pendingTags by remember { mutableStateOf<List<LocalTag>>(emptyList()) }
    var pendingExcludedTags by remember { mutableStateOf<List<LocalTag>>(emptyList()) }
    var pendingIncludeUnratedTags by remember { mutableStateOf(false) }
    var pendingIncludeSpoilerTags by remember { mutableStateOf(false) }
    var pendingHideFinished by remember { mutableStateOf(false) }

    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadTags()
        viewModel.load()
    }

    MediaListContent(
        category = category,
        data = data,
        error = error,
        isLoading = isLoading == true,
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        showFilterSheet = showFilterSheet,
        onOpenDrawer = onOpenDrawer,
        onSearchToggle = {
            if (isSearchActive) {
                searchQuery = ""
                viewModel.searchQuery = null
                viewModel.reload()
            }
            isSearchActive = !isSearchActive
        },
        onSearchQueryChange = { searchQuery = it },
        onSearchSubmit = {
            viewModel.searchQuery = searchQuery.trim().ifBlank { null }
            viewModel.reload()
        },
        onFilterClick = { showFilterSheet = true },
        onDismissFilterSheet = { showFilterSheet = false },
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        refreshError = viewModel.refreshError,
    ) {
        MediaFilterSheet(
            category = category,
            sortCriteria = pendingSortCriteria,
            type = pendingType,
            language = pendingLanguage,
            genres = pendingGenres,
            excludedGenres = pendingExcludedGenres,
            fskConstraints = pendingFskConstraints,
            tags = pendingTags,
            excludedTags = pendingExcludedTags,
            includeUnratedTags = pendingIncludeUnratedTags,
            includeSpoilerTags = pendingIncludeSpoilerTags,
            hideFinished = pendingHideFinished,
            genreData = genreData ?: emptyList(),
            tagData = tagData ?: emptyList(),
            onSortChange = { pendingSortCriteria = it },
            onTypeChange = { pendingType = it },
            onLanguageChange = { pendingLanguage = it },
            onGenresChange = { pendingGenres = it },
            onExcludedGenresChange = { pendingExcludedGenres = it },
            onFskConstraintsChange = { pendingFskConstraints = it },
            onTagsChange = { pendingTags = it },
            onExcludedTagsChange = { pendingExcludedTags = it },
            onIncludeUnratedTagsChange = { pendingIncludeUnratedTags = it },
            onIncludeSpoilerTagsChange = { pendingIncludeSpoilerTags = it },
            onHideFinishedChange = { pendingHideFinished = it },
            onSearch = {
                viewModel.searchQuery = searchQuery.trim().ifBlank { null }
                viewModel.language = pendingLanguage
                viewModel.genres = pendingGenres
                viewModel.excludedGenres = pendingExcludedGenres
                viewModel.fskConstraints = enumSetOf(pendingFskConstraints.toList())
                viewModel.tags = pendingTags
                viewModel.excludedTags = pendingExcludedTags
                viewModel.tagRateFilter =
                    if (pendingIncludeUnratedTags) TagRateFilter.ALL else TagRateFilter.RATED_ONLY
                viewModel.tagSpoilerFilter =
                    if (pendingIncludeSpoilerTags) TagSpoilerFilter.ALL else TagSpoilerFilter.NO_SPOILERS
                viewModel.hideFinished = pendingHideFinished
                // sortCriteria and type have Delegates.observable that call reload() automatically
                viewModel.sortCriteria = pendingSortCriteria
                viewModel.type = pendingType
                // Explicit reload to cover the case where sort/type didn't change
                viewModel.reload()
                showFilterSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaListContent(
    category: Category,
    data: List<MediaListEntry>?,
    error: ErrorAction?,
    refreshError: LiveData<ErrorAction?>,
    isLoading: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    showFilterSheet: Boolean,
    onOpenDrawer: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    filterSheetContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissFilterSheet,
            sheetState = bottomSheetState,
        ) {
            filterSheetContent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProxerTopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            stringResource(
                                if (category == Category.ANIME) R.string.section_anime else R.string.section_manga,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.action_filter))
                    }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data ?: emptyList(), key = { it.id }) { entry ->
                    val activity = context as? Activity ?: return@items
                    MediaCard(
                        entry = entry,
                        category = category,
                        onClick = { MediaActivity.navigateTo(activity, entry.id, entry.name, category) },
                    )
                }
                if (isLoading && !data.isNullOrEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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

@Composable
private fun MediaCard(entry: MediaListEntry, category: Category, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ProxerUrls.entryImage(entry.id).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = entry.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.medium.toAppString(context),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = context.getQuantityString(
                        when (category) {
                            Category.ANIME -> R.plurals.media_episode_count
                            else -> R.plurals.media_chapter_count
                        },
                        entry.episodeAmount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val languages = entry.languages.map { it.toGeneralLanguage() }.distinct()
                if (languages.contains(Language.GERMAN)) {
                    Image(
                        painter = painterResource(R.drawable.ic_germany),
                        contentDescription = stringResource(R.string.language_german),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (languages.contains(Language.ENGLISH)) {
                    Image(
                        painter = painterResource(R.drawable.ic_united_states),
                        contentDescription = stringResource(R.string.language_english),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (entry.rating > 0) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "%.1f".format(entry.rating / 2.0f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaFilterSheet(
    category: Category,
    sortCriteria: MediaSearchSortCriteria,
    type: MediaType,
    language: Language?,
    genres: List<LocalTag>,
    excludedGenres: List<LocalTag>,
    fskConstraints: Set<FskConstraint>,
    tags: List<LocalTag>,
    excludedTags: List<LocalTag>,
    includeUnratedTags: Boolean,
    includeSpoilerTags: Boolean,
    hideFinished: Boolean,
    genreData: List<LocalTag>,
    tagData: List<LocalTag>,
    onSortChange: (MediaSearchSortCriteria) -> Unit,
    onTypeChange: (MediaType) -> Unit,
    onLanguageChange: (Language?) -> Unit,
    onGenresChange: (List<LocalTag>) -> Unit,
    onExcludedGenresChange: (List<LocalTag>) -> Unit,
    onFskConstraintsChange: (Set<FskConstraint>) -> Unit,
    onTagsChange: (List<LocalTag>) -> Unit,
    onExcludedTagsChange: (List<LocalTag>) -> Unit,
    onIncludeUnratedTagsChange: (Boolean) -> Unit,
    onIncludeSpoilerTagsChange: (Boolean) -> Unit,
    onHideFinishedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Sort order
        Text(stringResource(R.string.action_sort), style = MaterialTheme.typography.titleSmall)
        val sortOptions = listOf(
            MediaSearchSortCriteria.RATING to stringResource(R.string.action_sort_rating),
            MediaSearchSortCriteria.CLICKS to stringResource(R.string.action_sort_clicks),
            MediaSearchSortCriteria.EPISODE_AMOUNT to stringResource(R.string.action_sort_count),
            MediaSearchSortCriteria.NAME to stringResource(R.string.action_sort_name),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sortOptions.forEach { (criteria, label) ->
                FilterChip(
                    selected = sortCriteria == criteria,
                    onClick = { onSortChange(criteria) },
                    label = { Text(label) },
                )
            }
        }

        HorizontalDivider()

        // Media type
        Text(stringResource(R.string.action_filter), style = MaterialTheme.typography.titleSmall)
        val typeOptions: List<Pair<MediaType, String>> = if (category == Category.ANIME) {
            listOf(
                MediaType.ALL_ANIME to stringResource(R.string.action_filter_all),
                MediaType.ANIMESERIES to stringResource(R.string.action_filter_anime_series),
                MediaType.MOVIE to stringResource(R.string.action_filter_movies),
                MediaType.OVA to stringResource(R.string.action_filter_ova),
                MediaType.HENTAI to stringResource(R.string.action_filter_hentai),
            )
        } else {
            listOf(
                MediaType.ALL_MANGA to stringResource(R.string.action_filter_all),
                MediaType.MANGASERIES to stringResource(R.string.action_filter_manga_series),
                MediaType.ONESHOT to stringResource(R.string.action_filter_one_shots),
                MediaType.DOUJIN to stringResource(R.string.action_filter_doujin),
                MediaType.HMANGA to stringResource(R.string.action_filter_hmanga),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            typeOptions.forEach { (mediaType, label) ->
                FilterChip(
                    selected = type == mediaType,
                    onClick = { onTypeChange(mediaType) },
                    label = { Text(label) },
                )
            }
        }

        HorizontalDivider()

        // Language
        Text(stringResource(R.string.fragment_media_list_language), style = MaterialTheme.typography.titleSmall)
        val languageOptions = listOf(
            null to stringResource(R.string.fragment_media_list_all_languages),
            Language.GERMAN to stringResource(R.string.language_german),
            Language.ENGLISH to stringResource(R.string.language_english),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            languageOptions.forEach { (lang, label) ->
                FilterChip(
                    selected = language == lang,
                    onClick = { onLanguageChange(lang) },
                    label = { Text(label) },
                )
            }
        }

        HorizontalDivider()

        // Genres
        if (genreData.isNotEmpty()) {
            Text(stringResource(R.string.fragment_media_list_genres), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                genreData.forEach { tag ->
                    FilterChip(
                        selected = genres.any { it.id == tag.id },
                        onClick = {
                            val updated =
                                if (genres.any { it.id == tag.id }) {
                                    genres.filter { it.id != tag.id }
                                } else {
                                    genres + tag
                                }
                            onGenresChange(updated)
                        },
                        label = { Text(tag.name) },
                    )
                }
            }

            HorizontalDivider()

            // Excluded genres
            Text(
                stringResource(R.string.fragment_media_list_excluded_genres),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                genreData.forEach { tag ->
                    FilterChip(
                        selected = excludedGenres.any { it.id == tag.id },
                        onClick = {
                            val updated =
                                if (excludedGenres.any { it.id == tag.id }) {
                                    excludedGenres.filter { it.id != tag.id }
                                } else {
                                    excludedGenres + tag
                                }
                            onExcludedGenresChange(updated)
                        },
                        label = { Text(tag.name) },
                    )
                }
            }

            HorizontalDivider()
        }

        // FSK constraints
        Text(stringResource(R.string.fragment_media_list_fsk), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FskConstraint.values().forEach { fsk ->
                FilterChip(
                    selected = fsk in fskConstraints,
                    onClick = {
                        val updated =
                            if (fsk in fskConstraints) {
                                fskConstraints - fsk
                            } else {
                                fskConstraints + fsk
                            }
                        onFskConstraintsChange(updated)
                    },
                    label = { Text(fsk.toAppString(context)) },
                )
            }
        }

        HorizontalDivider()

        // Tags
        if (tagData.isNotEmpty()) {
            Text(stringResource(R.string.fragment_media_list_tags), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tagData.forEach { tag ->
                    FilterChip(
                        selected = tags.any { it.id == tag.id },
                        onClick = {
                            val updated =
                                if (tags.any { it.id == tag.id }) {
                                    tags.filter { it.id != tag.id }
                                } else {
                                    tags + tag
                                }
                            onTagsChange(updated)
                        },
                        label = { Text(tag.name) },
                    )
                }
            }

            HorizontalDivider()

            // Excluded tags
            Text(
                stringResource(R.string.fragment_media_list_excluded_tags),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tagData.forEach { tag ->
                    FilterChip(
                        selected = excludedTags.any { it.id == tag.id },
                        onClick = {
                            val updated =
                                if (excludedTags.any { it.id == tag.id }) {
                                    excludedTags.filter { it.id != tag.id }
                                } else {
                                    excludedTags + tag
                                }
                            onExcludedTagsChange(updated)
                        },
                        label = { Text(tag.name) },
                    )
                }
            }

            HorizontalDivider()
        }

        // Tag options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = includeUnratedTags, onCheckedChange = onIncludeUnratedTagsChange)
            Text(
                stringResource(R.string.fragment_media_list_include_unrated_tags),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = includeSpoilerTags, onCheckedChange = onIncludeSpoilerTagsChange)
            Text(
                stringResource(R.string.fragment_media_list_include_spoiler_tags),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = hideFinished, onCheckedChange = onHideFinishedChange)
            Text(
                stringResource(R.string.fragment_media_list_hide_finished),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.fragment_media_list_search))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaListContentPreview() {
    ProxerTheme {
        MediaListContent(
            category = Category.ANIME,
            data = null,
            error = null,
            refreshError = MutableLiveData(null),
            isLoading = true,
            isSearchActive = false,
            searchQuery = "",
            showFilterSheet = false,
            onOpenDrawer = {},
            onSearchToggle = {},
            onSearchQueryChange = {},
            onSearchSubmit = {},
            onFilterClick = {},
            onDismissFilterSheet = {},
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            filterSheetContent = {},
        )
    }
}
