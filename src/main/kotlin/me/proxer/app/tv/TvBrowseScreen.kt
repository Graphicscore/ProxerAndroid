package me.proxer.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import me.proxer.app.media.LocalTag
import me.proxer.app.media.list.MediaListViewModel
import me.proxer.app.util.extension.enumSetOf
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.Language
import me.proxer.library.enums.MediaSearchSortCriteria
import me.proxer.library.enums.MediaType
import me.proxer.library.enums.TagRateFilter
import me.proxer.library.enums.TagSpoilerFilter
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvBrowseScreen(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val viewModel: MediaListViewModel = koinViewModel {
        parametersOf(
            MediaSearchSortCriteria.RATING,
            MediaType.ANIMESERIES,
            null as String?,
            null as Language?,
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            enumSetOf<FskConstraint>(),
            emptyList<LocalTag>(),
            emptyList<LocalTag>(),
            null as TagRateFilter?,
            null as TagSpoilerFilter?,
            null as Boolean?
        )
    }

    val entries by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ProxerTV", fontSize = 24.sp, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSearchClick) { Text("Search") }
                OutlinedButton(onClick = onLoginClick) { Text("Sign In") }
            }
        }

        when {
            isLoading == true && entries.isNullOrEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error!!,
                        onLoginClick = onLoginClick,
                        onRetryClick = { viewModel.load() }
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries ?: emptyList()) { entry ->
                        TvMediaCard(entry = entry, onClick = { onMediaClick(entry.id, entry.name) })
                    }
                }
            }
        }
    }
}

@Composable
fun TvMediaCard(entry: MediaListEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .height(270.dp)
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(entry.id).toString(),
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(8.dp)
            ) {
                Text(
                    text = entry.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
