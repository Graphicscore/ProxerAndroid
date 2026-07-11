package me.proxer.app.media.recommendation

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.library.entity.info.Recommendation
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecommendationScreen(mediaId: String) {
    val viewModel = koinViewModel<RecommendationViewModel> { parametersOf(mediaId) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    RecommendationContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        onRetry = { viewModel.load() },
    )
}

@Composable
private fun RecommendationContent(
    data: List<Recommendation>?,
    error: ErrorAction?,
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    ContentScreen(
        isLoading = isLoading,
        error = error,
        onRetry = onRetry,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(data ?: emptyList(), key = { it.id }) { recommendation ->
                RecommendationCard(recommendation = recommendation)
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: Recommendation) {
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        onClick = {
            if (activity != null) {
                MediaActivity.navigateTo(activity, recommendation.id, recommendation.name, recommendation.category)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(recommendation.id).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f),
            )
            Text(
                text = recommendation.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
            if (recommendation.rating > 0) {
                Text(
                    text = "★ ${"%.1f".format(recommendation.rating / 2.0f)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecommendationContentPreview() {
    ProxerTheme {
        RecommendationContent(
            data = null,
            error = null,
            isLoading = true,
            onRetry = {},
        )
    }
}
