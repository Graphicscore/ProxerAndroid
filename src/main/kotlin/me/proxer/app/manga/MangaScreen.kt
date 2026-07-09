package me.proxer.app.manga

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.PAN_LIMIT_INSIDE
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.ZOOM_FOCUS_CENTER
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.DeviceUtils
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.GLUtil
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.extension.decodedName
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.wrapper.OriginalSizeGlideTarget
import me.proxer.library.entity.manga.Chapter
import me.proxer.library.entity.manga.Page
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(
    id: String,
    initialEpisode: Int,
    language: Language,
    initialName: String?,
    initialChapterTitle: String?,
    initialEpisodeAmount: Int?,
    onBack: () -> Unit,
) {
    val preferenceHelper = koinInject<PreferenceHelper>()
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var currentEpisode by rememberSaveable { mutableIntStateOf(initialEpisode) }
    var displayName by rememberSaveable { mutableStateOf(initialName) }
    var displayChapterTitle by rememberSaveable { mutableStateOf(initialChapterTitle) }
    var totalEpisodes by rememberSaveable { mutableStateOf(initialEpisodeAmount) }
    var readerOrientation by rememberSaveable { mutableStateOf(preferenceHelper.mangaReaderOrientation) }
    var isFullscreen by remember { mutableStateOf(false) }

    val initialEpisode = remember { currentEpisode }
    val viewModel = koinViewModel<MangaViewModel> { parametersOf(id, language, initialEpisode) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lowMemoryMessage = stringResource(R.string.fragment_manga_low_memory)

    val userStateSaved by viewModel.userStateData.observeAsState()
    val userStateErr by viewModel.userStateError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(userStateSaved) {
        if (userStateSaved != null) {
            snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success))
        }
    }

    LaunchedEffect(userStateErr) {
        val err = userStateErr
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_set_user_info, context.getString(err.message)),
            )
        }
    }

    // Update displayed metadata from loaded chapter data
    LaunchedEffect(data) {
        data?.let {
            displayName = it.name
            displayChapterTitle = it.chapter.title
            totalEpisodes = it.episodeAmount
        }
    }

    // Fullscreen: hide system bars when content is ready, show during loading/error
    LaunchedEffect(data, error) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (data != null && error == null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            isFullscreen = true
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            isFullscreen = false
        }
    }

    val episodeLabel = remember(currentEpisode) {
        Category.MANGA.toEpisodeAppString(context, currentEpisode)
    }

    MangaContent(
        currentEpisode = currentEpisode,
        totalEpisodes = totalEpisodes,
        displayName = displayName,
        displayChapterTitle = displayChapterTitle,
        episodeLabel = episodeLabel,
        data = data,
        error = error,
        isLoading = isLoading == true,
        isFullscreen = isFullscreen,
        readerOrientation = readerOrientation,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleOrientation = {
            readerOrientation = when (readerOrientation) {
                MangaReaderOrientation.LEFT_TO_RIGHT -> MangaReaderOrientation.RIGHT_TO_LEFT
                MangaReaderOrientation.RIGHT_TO_LEFT -> MangaReaderOrientation.VERTICAL
                MangaReaderOrientation.VERTICAL -> MangaReaderOrientation.LEFT_TO_RIGHT
            }
            preferenceHelper.mangaReaderOrientation = readerOrientation
        },
        onPreviousEpisode = {
            if (currentEpisode > 1) {
                currentEpisode--
                viewModel.setEpisode(currentEpisode)
            }
        },
        onNextEpisode = {
            val total = totalEpisodes
            if (total != null && currentEpisode < total) {
                currentEpisode++
                viewModel.setEpisode(currentEpisode)
            }
        },
        onRetry = { viewModel.load() },
        onLowMemory = { scope.launch { snackbarHostState.showSnackbar(lowMemoryMessage) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaContent(
    currentEpisode: Int,
    totalEpisodes: Int?,
    displayName: String?,
    displayChapterTitle: String?,
    episodeLabel: String,
    data: MangaChapterInfo?,
    error: ErrorAction?,
    isLoading: Boolean,
    isFullscreen: Boolean,
    readerOrientation: MangaReaderOrientation,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onRetry: () -> Unit,
    onLowMemory: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            val name = displayName
                            if (name != null) {
                                Text(text = name, maxLines = 1)
                            }
                            val subtitle = displayChapterTitle?.takeIf { it.isNotBlank() }
                                ?: episodeLabel
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleOrientation) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.fragment_manga_toggle_orientation),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                val total = totalEpisodes
                if (total != null) {
                    BottomAppBar {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = onPreviousEpisode,
                                enabled = currentEpisode > 1,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.fragment_manga_previous_chapter),
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "$episodeLabel / $total",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onNextEpisode,
                                enabled = currentEpisode < total,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.fragment_manga_next_chapter),
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
            modifier = if (isFullscreen) Modifier else Modifier.padding(padding),
        ) {
            val chapterData = data ?: return@ContentScreen
            val pages = chapterData.chapter.pages ?: return@ContentScreen

            when (readerOrientation) {
                MangaReaderOrientation.VERTICAL -> {
                    val screenWidth = remember { DeviceUtils.getScreenWidth(context) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(pages, key = { it.decodedName }) { page ->
                            MangaPage(
                                page = page,
                                chapter = chapterData.chapter,
                                isVertical = true,
                                screenWidth = screenWidth,
                                onLowMemory = onLowMemory,
                            )
                        }
                    }
                }

                MangaReaderOrientation.LEFT_TO_RIGHT, MangaReaderOrientation.RIGHT_TO_LEFT -> {
                    val displayPages = if (readerOrientation == MangaReaderOrientation.RIGHT_TO_LEFT) {
                        pages.reversed()
                    } else {
                        pages
                    }
                    val pagerState = rememberPagerState { displayPages.size }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        MangaPage(
                            page = displayPages[pageIndex],
                            chapter = chapterData.chapter,
                            isVertical = false,
                            screenWidth = 0,
                            onLowMemory = onLowMemory,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MangaContentPreview() {
    ProxerTheme {
        MangaContent(
            currentEpisode = 1,
            totalEpisodes = 12,
            displayName = "My Manga",
            displayChapterTitle = null,
            episodeLabel = "Chapter 1",
            data = null,
            error = null,
            isLoading = true,
            isFullscreen = false,
            readerOrientation = MangaReaderOrientation.LEFT_TO_RIGHT,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onToggleOrientation = {},
            onPreviousEpisode = {},
            onNextEpisode = {},
            onRetry = {},
            onLowMemory = {},
        )
    }
}

@Composable
private fun MangaPage(
    page: Page,
    chapter: Chapter,
    isVertical: Boolean,
    screenWidth: Int,
    onLowMemory: () -> Unit,
) {
    if (page.decodedName.endsWith(".gif", ignoreCase = true)) {
        MangaGifPage(page = page, chapter = chapter, isVertical = isVertical, screenWidth = screenWidth)
    } else {
        MangaImagePage(
            page = page,
            chapter = chapter,
            isVertical = isVertical,
            screenWidth = screenWidth,
            onLowMemory = onLowMemory,
        )
    }
}

@Composable
private fun MangaImagePage(
    page: Page,
    chapter: Chapter,
    isVertical: Boolean,
    screenWidth: Int,
    onLowMemory: () -> Unit,
) {
    val context = LocalContext.current
    val pageUrl = remember(chapter.server, chapter.entryId, chapter.id, page.decodedName) {
        ProxerUrls.mangaPageImage(chapter.server, chapter.entryId, chapter.id, page.decodedName).toString()
    }

    // Shared reference between DisposableEffect and AndroidView.
    // The Glide callback fires asynchronously after the factory has set viewRef[0].
    val viewRef = remember { arrayOfNulls<SubsamplingScaleImageView>(1) }
    var hasError by remember(pageUrl) { mutableStateOf(false) }

    DisposableEffect(pageUrl) {
        val glide = Glide.with(context)
        val target = object : OriginalSizeGlideTarget<File>() {
            override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                Single
                    .fromCallable { ImageSource.uri(resource.path) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeAndLogErrors { source -> viewRef[0]?.setImage(source) }
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                hasError = true
            }
        }

        glide.downloadOnly().load(pageUrl).into(target)

        onDispose {
            glide.clear(target)
            viewRef[0]?.recycle()
        }
    }

    val itemModifier = if (isVertical) {
        val density = LocalDensity.current
        val aspectHeightPx = if (page.width > 0) {
            (page.height * screenWidth.toFloat() / page.width.toFloat()).toInt()
        } else {
            screenWidth
        }
        Modifier
            .fillMaxWidth()
            .height(with(density) { aspectHeightPx.toDp() })
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = itemModifier) {
        if (hasError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
        } else if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        } else {
            AndroidView(
                factory = { ctx ->
                    val shortAnimTime = ctx.resources.getInteger(android.R.integer.config_shortAnimTime)
                    SubsamplingScaleImageView(ctx).apply {
                        setDoubleTapZoomDuration(shortAnimTime)
                        setDoubleTapZoomStyle(ZOOM_FOCUS_CENTER)
                        setMaxTileSize(GLUtil.maxTextureSize)
                        setPanLimit(PAN_LIMIT_INSIDE)
                        setMinimumTileDpi(196)
                        setMinimumDpi(90)

                        setOnImageEventListener(
                            object : SubsamplingScaleImageView.OnImageEventListener {
                                override fun onReady() {
                                    val newMaxScale = minScale * 2.5f
                                    setDoubleTapZoomScale(newMaxScale)
                                    maxScale = newMaxScale
                                    setScaleAndCenter(scale, center?.also { it.y = 0f })
                                }

                                override fun onImageLoaded() = Unit

                                override fun onPreviewLoadError(e: Exception) = Unit

                                override fun onImageLoadError(e: Exception) {
                                    if (e.cause is OutOfMemoryError) {
                                        onLowMemory()
                                    } else {
                                        hasError = true
                                    }
                                }

                                override fun onTileLoadError(e: Exception) = Unit

                                override fun onPreviewReleased() = Unit
                            },
                        )
                    }.also { viewRef[0] = it }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MangaGifPage(
    page: Page,
    chapter: Chapter,
    isVertical: Boolean,
    screenWidth: Int,
) {
    val pageUrl = remember(chapter.server, chapter.entryId, chapter.id, page.decodedName) {
        ProxerUrls.mangaPageImage(chapter.server, chapter.entryId, chapter.id, page.decodedName).toString()
    }

    var hasError by remember(pageUrl) { mutableStateOf(false) }

    val itemModifier = if (isVertical) {
        val density = LocalDensity.current
        val aspectHeightPx = if (page.width > 0) {
            (page.height * screenWidth.toFloat() / page.width.toFloat()).toInt()
        } else {
            screenWidth
        }
        Modifier
            .fillMaxWidth()
            .height(with(density) { aspectHeightPx.toDp() })
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = itemModifier) {
        if (hasError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
        } else if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    val currentUrl = view.getTag(R.id.tag_manga_url) as? String
                    if (currentUrl != pageUrl) {
                        view.setTag(R.id.tag_manga_url, pageUrl)
                        Glide.with(view.context)
                            .asGif()
                            .load(pageUrl)
                            .into(
                                object : ImageViewTarget<GifDrawable>(view) {
                                    override fun setResource(resource: GifDrawable?) {
                                        view.setImageDrawable(resource)
                                    }

                                    override fun onLoadFailed(errorDrawable: Drawable?) {
                                        hasError = true
                                    }
                                },
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
