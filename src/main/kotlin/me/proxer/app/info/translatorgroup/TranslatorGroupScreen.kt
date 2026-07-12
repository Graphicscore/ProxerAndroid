package me.proxer.app.info.translatorgroup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.startActivityOrToast
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toCategory
import me.proxer.library.entity.info.TranslatorGroup
import me.proxer.library.entity.list.TranslatorGroupProject
import me.proxer.library.enums.Country
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorGroupScreen(id: String, initialName: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val infoViewModel = koinViewModel<TranslatorGroupInfoViewModel> { parametersOf(id) }
    val data by infoViewModel.data.observeAsState()

    val displayName = data?.name ?: initialName ?: ""

    val tabs = listOf(R.string.section_translator_group_info, R.string.section_translator_group_projects)
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    TranslatorGroupScreenContent(
        displayName = displayName,
        selectedTab = pagerState.currentPage,
        tabs = tabs,
        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
        onBack = onBack,
        onShare = if (displayName.isNotBlank()) {
            {
                (context as? Activity)?.let { activity ->
                    ShareCompat
                        .IntentBuilder(activity)
                        .setText(
                            context.getString(
                                R.string.share_translator_group,
                                displayName,
                                ProxerUrls.translatorGroupWeb(id),
                            ),
                        )
                        .setType("text/plain")
                        .setChooserTitle(context.getString(R.string.share_title))
                        .startChooser()
                }
            }
        } else {
            null
        },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TranslatorGroupInfoTab(id = id)
                1 -> TranslatorGroupProjectsTab(id = id)
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslatorGroupScreenContent(
    displayName: String,
    selectedTab: Int,
    tabs: List<Int>,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    tabContent: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }
            tabContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TranslatorGroupScreenContentPreview() {
    ProxerTheme {
        TranslatorGroupScreenContent(
            displayName = "Example Translator Group",
            selectedTab = 0,
            tabs = listOf(R.string.section_translator_group_info, R.string.section_translator_group_projects),
            onTabSelected = {},
            onBack = {},
            onShare = null,
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TranslatorGroupInfoTab(id: String) {
    val viewModel = koinViewModel<TranslatorGroupInfoViewModel> { parametersOf(id) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ContentScreen(
        isLoading = isLoading == true,
        error = error,
        onRetry = { viewModel.load() },
    ) {
        if (data != null) {
            TranslatorGroupInfoBody(data = data!!)
        }
    }
}

@Composable
private fun TranslatorGroupInfoBody(data: TranslatorGroup) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (data.country != Country.NONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.fragment_translator_group_language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f),
                )
                Image(
                    painter = painterResource(data.country.toDrawableRes()),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        val linkText = data.link?.toString()
        if (!linkText.isNullOrBlank()) {
            if (data.country != Country.NONE) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.fragment_translator_group_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f),
                )
                Text(
                    text = linkText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(0.6f)
                        .clickable {
                            context.startActivityOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(linkText)))
                        },
                )
            }
        }

        if (data.description.isNotBlank()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.fragment_translator_group_description),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TranslatorGroupProjectsTab(id: String) {
    val viewModel = koinViewModel<TranslatorGroupProjectViewModel> { parametersOf(id) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    ObserveLiveDataEvent(viewModel.refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data ?: emptyList(), key = { it.id }) { project ->
                    TranslatorGroupProjectItem(project = project)
                }
                if (isLoading == true && !data.isNullOrEmpty()) {
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
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TranslatorGroupProjectItem(project: TranslatorGroupProject) {
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        onClick = {
            activity?.let {
                MediaActivity.navigateTo(it, project.id, project.name, project.medium.toCategory())
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(project.id).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f),
            )
            Text(
                text = project.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = project.medium.toAppString(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = project.state.toAppString(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (project.rating > 0) {
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
                        text = "%.1f".format(project.rating / 2.0f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@DrawableRes
private fun Country.toDrawableRes(): Int = when (this) {
    Country.GERMANY -> R.drawable.ic_germany
    Country.ENGLAND -> R.drawable.ic_united_states
    Country.UNITED_STATES -> R.drawable.ic_united_states
    Country.JAPAN -> R.drawable.ic_japan
    Country.KOREA -> R.drawable.ic_korea
    Country.CHINA -> R.drawable.ic_china
    Country.INTERNATIONAL, Country.OTHER, Country.NONE -> R.drawable.ic_united_nations
}
