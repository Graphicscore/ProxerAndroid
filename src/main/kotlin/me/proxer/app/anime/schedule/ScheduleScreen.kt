package me.proxer.app.anime.schedule

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.util.extension.formattedDistanceTo
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toLocalDateTimeBP
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<ScheduleViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_schedule)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true,
            error = error,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            val schedule = data ?: return@ContentScreen
            val context = LocalContext.current
            val activity = context as Activity
            val sortedDays = remember(schedule) {
                schedule.entries.sortedBy { (day, _) -> day.ordinal }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                sortedDays.forEach { (day, dayEntries) ->
                    stickyHeader(key = "header_${day.ordinal}") {
                        ScheduleDayHeader(day = day)
                    }
                    item(key = "row_${day.ordinal}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(dayEntries, key = { it.id }) { entry ->
                                ScheduleEntryCard(
                                    entry = entry,
                                    onClick = {
                                        MediaActivity.navigateTo(activity, entry.entryId, entry.name)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayHeader(day: CalendarDay) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            text = day.toAppString(context),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ScheduleEntryCard(entry: CalendarEntry, onClick: () -> Unit) {
    val context = LocalContext.current

    val hourMinuteFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN) }

    val airingInfoText = remember(entry.id) {
        val airDateTime = entry.date.toLocalDateTimeBP()
        val uploadDateTime = entry.uploadDate.toLocalDateTimeBP()
        val airTime = hourMinuteFormatter.format(airDateTime)
        val uploadTime = hourMinuteFormatter.format(uploadDateTime)
        val uploadDateText = if (uploadDateTime.toLocalDate() != airDateTime.toLocalDate()) {
            "${uploadDateTime.format(dayFormatter)}, $uploadTime"
        } else {
            uploadTime
        }
        if (entry.date == entry.uploadDate) {
            context.getString(R.string.fragment_schedule_airing, airTime)
        } else {
            context.getString(R.string.fragment_schedule_airing_upload, airTime, uploadDateText)
        }
    }

    var statusText by remember(entry.id) { mutableStateOf("") }
    LaunchedEffect(entry.id) {
        while (true) {
            val now = LocalDateTime.now()
            val uploadDateTime = entry.uploadDate.toLocalDateTimeBP()
            val airDateTime = entry.date.toLocalDateTimeBP()
            statusText = when {
                uploadDateTime.isBefore(now) -> {
                    if (entry.date == entry.uploadDate) {
                        context.getString(R.string.fragment_schedule_aired)
                    } else {
                        context.getString(R.string.fragment_schedule_uploaded)
                    }
                }
                airDateTime.isBefore(now) -> {
                    context.getString(
                        R.string.fragment_schedule_aired_remaining_time,
                        now.formattedDistanceTo(uploadDateTime),
                    )
                }
                else -> {
                    context.getString(
                        R.string.fragment_schedule_remaining_time,
                        now.formattedDistanceTo(airDateTime),
                    )
                }
            }
            delay(1_000)
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ProxerUrls.entryImage(entry.entryId).toString(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = entry.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.fragment_schedule_episode, entry.episode.toString()),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (entry.rating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            text = "%.1f / 5".format(entry.rating / 2.0f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    text = airingInfoText,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
