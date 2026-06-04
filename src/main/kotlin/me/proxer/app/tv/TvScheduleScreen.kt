package me.proxer.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import me.proxer.app.anime.schedule.ScheduleViewModel
import me.proxer.app.tv.detail.TvMediaDetailActivity
import me.proxer.app.util.extension.toAppString
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat

@Composable
fun TvScheduleScreen() {
    val viewModel: ScheduleViewModel = koinViewModel()
    val schedule by viewModel.data.observeAsState(emptyMap())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    when {
        isLoading == true && schedule.isNullOrEmpty() -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                TvErrorView(
                    error = error!!,
                    onRetryClick = { viewModel.load() },
                )
            }
        }

        else -> {
            val sortedDays =
                (schedule ?: emptyMap())
                    .entries
                    .sortedBy { (day, _) -> day.ordinal }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                sortedDays.forEach { (day, dayEntries) ->
                    item(key = day.ordinal) {
                        TvScheduleDayRow(
                            day = day,
                            entries = dayEntries,
                            onEntryClick = { entry ->
                                TvMediaDetailActivity.navigateTo(context, entry.entryId, entry.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvScheduleDayRow(
    day: CalendarDay,
    entries: List<CalendarEntry>,
    onEntryClick: (CalendarEntry) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = day.toAppString(context),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                TvScheduleCard(entry = entry, onClick = { onEntryClick(entry) })
            }
        }
    }
}

@Composable
private fun TvScheduleCard(
    entry: CalendarEntry,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(160.dp).height(220.dp),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(entry.entryId).toString(),
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
            ) {
                Text(
                    text = entry.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateFormat.getTimeInstance(DateFormat.SHORT).format(entry.date),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}
