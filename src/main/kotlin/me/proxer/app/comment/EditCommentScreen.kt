package me.proxer.app.comment

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.ui.view.bbcode.toSimpleBBTree
import me.proxer.app.util.ErrorUtils.ErrorAction
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommentScreen(
    id: String?,
    entryId: String?,
    name: String?,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<EditCommentViewModel> { parametersOf(id, entryId) }
    val data by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val isUpdate by viewModel.isUpdate.observeAsState(id.isNullOrBlank().not())
    val publishResult by viewModel.publishResult.observeAsState()
    val publishError by viewModel.publishError.observeAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var currentRating by remember { mutableFloatStateOf(0f) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(data) {
        data?.let { comment ->
            if (textFieldValue.text.isBlank() && comment.content.isNotBlank()) {
                textFieldValue = TextFieldValue(comment.content)
            }
            currentRating = comment.overallRating / 2f
        }
    }

    LaunchedEffect(publishResult) {
        val comment = publishResult ?: return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.setResult(Activity.RESULT_OK, Intent().putExtra(EditCommentActivity.COMMENT_EXTRA, comment))
        Toast.makeText(context, R.string.fragment_edit_comment_published, Toast.LENGTH_SHORT).show()
        activity.finish()
    }

    LaunchedEffect(publishError) {
        publishError?.let { errorAction ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_comment_publish, context.getString(errorAction.message)),
                )
            }
        }
    }

    EditCommentContent(
        isUpdate = isUpdate == true,
        name = name,
        isLoading = isLoading == true,
        error = error,
        textFieldValue = textFieldValue,
        currentRating = currentRating,
        selectedTab = selectedTab,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPublish = { viewModel.publish() },
        onTextChanged = { newValue ->
            textFieldValue = newValue
            viewModel.updateContent(newValue.text)
        },
        onRatingChanged = { newRating ->
            currentRating = newRating
            viewModel.updateRating(newRating)
        },
        onInsertTag = { tag, value ->
            textFieldValue = insertTag(textFieldValue, tag, value)
            viewModel.updateContent(textFieldValue.text)
        },
        onTabSelected = { selectedTab = it },
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCommentContent(
    isUpdate: Boolean,
    name: String?,
    isLoading: Boolean,
    error: ErrorAction?,
    textFieldValue: TextFieldValue,
    currentRating: Float,
    selectedTab: Int,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    onTextChanged: (TextFieldValue) -> Unit,
    onRatingChanged: (Float) -> Unit,
    onInsertTag: (String, String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (isUpdate) R.string.action_update_comment else R.string.action_create_comment))
                        name?.trim()?.takeIf { it.isNotEmpty() }?.let { subtitle ->
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onPublish) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.action_publish),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        text = { Text(stringResource(R.string.fragment_edit_comment)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = { Text(stringResource(R.string.fragment_edit_comment_preview)) },
                    )
                }

                when (selectedTab) {
                    0 -> EditTab(
                        textFieldValue = textFieldValue,
                        onTextChanged = onTextChanged,
                        rating = currentRating,
                        onRatingChanged = onRatingChanged,
                        onInsertTag = onInsertTag,
                    )
                    1 -> PreviewTab(text = textFieldValue.text)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditCommentContentPreview() {
    ProxerTheme {
        EditCommentContent(
            isUpdate = false,
            name = "My Anime",
            isLoading = true,
            error = null,
            textFieldValue = TextFieldValue(""),
            currentRating = 0f,
            selectedTab = 0,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onPublish = {},
            onTextChanged = {},
            onRatingChanged = {},
            onInsertTag = { _, _ -> },
            onTabSelected = {},
            onRetry = {},
        )
    }
}

@Composable
private fun EditTab(
    textFieldValue: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    rating: Float,
    onRatingChanged: (Float) -> Unit,
    onInsertTag: (String, String) -> Unit,
) {
    var showSizeMenu by remember { mutableStateOf(false) }
    var showColorMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(getRatingTitle(rating)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Slider(
                value = rating,
                onValueChange = onRatingChanged,
                valueRange = 0f..5f,
                steps = 9,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onTextChanged,
            placeholder = { Text(stringResource(R.string.fragment_edit_comment_hint)) },
            supportingText = {
                Text(
                    text = "${textFieldValue.text.length} / 20000",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            minLines = 5,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            IconButton(onClick = { onInsertTag("b", "") }) {
                Icon(Icons.Default.FormatBold, contentDescription = stringResource(R.string.fragment_edit_comment_bold))
            }
            IconButton(onClick = { onInsertTag("i", "") }) {
                Icon(Icons.Default.FormatItalic, contentDescription = stringResource(R.string.fragment_edit_comment_italic))
            }
            IconButton(onClick = { onInsertTag("u", "") }) {
                Icon(Icons.Default.FormatUnderlined, contentDescription = stringResource(R.string.fragment_edit_comment_underline))
            }
            IconButton(onClick = { onInsertTag("s", "") }) {
                Icon(Icons.Default.FormatStrikethrough, contentDescription = stringResource(R.string.fragment_edit_comment_strikethrough))
            }
            Box {
                IconButton(onClick = { showSizeMenu = true }) {
                    Icon(Icons.Default.FormatSize, contentDescription = stringResource(R.string.fragment_edit_comment_size))
                }
                DropdownMenu(expanded = showSizeMenu, onDismissRequest = { showSizeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_size_huge)) },
                        onClick = { onInsertTag("size", "5"); showSizeMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_size_large)) },
                        onClick = { onInsertTag("size", "4"); showSizeMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_size_normal)) },
                        onClick = { onInsertTag("size", "3"); showSizeMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_size_small)) },
                        onClick = { onInsertTag("size", "2"); showSizeMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_size_tiny)) },
                        onClick = { onInsertTag("size", "1"); showSizeMenu = false },
                    )
                }
            }
            Box {
                IconButton(onClick = { showColorMenu = true }) {
                    Icon(Icons.Default.FormatColorFill, contentDescription = stringResource(R.string.fragment_edit_comment_color))
                }
                DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_red)) },
                        onClick = { onInsertTag("color", "#e53935"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_purple)) },
                        onClick = { onInsertTag("color", "#8e24aa"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_blue)) },
                        onClick = { onInsertTag("color", "#1e88e5"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_green)) },
                        onClick = { onInsertTag("color", "#43a047"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_yellow)) },
                        onClick = { onInsertTag("color", "#fdd835"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_orange)) },
                        onClick = { onInsertTag("color", "#fb8c00"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_grey)) },
                        onClick = { onInsertTag("color", "#757575"); showColorMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fragment_edit_comment_color_white)) },
                        onClick = { onInsertTag("color", "#ffffff"); showColorMenu = false },
                    )
                }
            }
            IconButton(onClick = { onInsertTag("left", "") }) {
                Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = stringResource(R.string.fragment_edit_comment_left))
            }
            IconButton(onClick = { onInsertTag("center", "") }) {
                Icon(Icons.Default.FormatAlignCenter, contentDescription = stringResource(R.string.fragment_edit_comment_center))
            }
            IconButton(onClick = { onInsertTag("right", "") }) {
                Icon(Icons.AutoMirrored.Filled.FormatAlignRight, contentDescription = stringResource(R.string.fragment_edit_comment_right))
            }
            IconButton(onClick = { onInsertTag("spoiler", "") }) {
                Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.fragment_edit_comment_spoiler))
            }
        }
    }
}

@Composable
private fun PreviewTab(text: String) {
    val parsedContent = remember(text) { text.toSimpleBBTree() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (parsedContent.isBlank()) {
            Text(
                text = stringResource(R.string.fragment_edit_comment_preview_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            AndroidView(
                factory = { ctx -> BBCodeView(ctx).apply { expandSpoilers = true } },
                update = { view -> view.tree = parsedContent },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun insertTag(textFieldValue: TextFieldValue, tag: String, value: String = ""): TextFieldValue {
    val startTag = "[$tag${if (value.isNotEmpty()) "=$value" else ""}]"
    val endTag = "[/$tag]"
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    return if (selection.start == selection.end) {
        val pos = selection.start.coerceIn(0, text.length)
        val newText = text.substring(0, pos) + startTag + endTag + text.substring(pos)
        textFieldValue.copy(text = newText, selection = TextRange(pos + startTag.length))
    } else {
        val start = selection.min
        val end = selection.max
        val newText = text.substring(0, start) + startTag + text.substring(start, end) + endTag + text.substring(end)
        textFieldValue.copy(
            text = newText,
            selection = TextRange(start + startTag.length + (end - start) + endTag.length),
        )
    }
}

private fun getRatingTitle(rating: Float) = when ((rating * 2).toInt()) {
    1 -> R.string.fragment_edit_comment_rating_title_1
    2 -> R.string.fragment_edit_comment_rating_title_2
    3 -> R.string.fragment_edit_comment_rating_title_3
    4 -> R.string.fragment_edit_comment_rating_title_4
    5 -> R.string.fragment_edit_comment_rating_title_5
    6 -> R.string.fragment_edit_comment_rating_title_6
    7 -> R.string.fragment_edit_comment_rating_title_7
    8 -> R.string.fragment_edit_comment_rating_title_8
    9 -> R.string.fragment_edit_comment_rating_title_9
    10 -> R.string.fragment_edit_comment_rating_title_10
    else -> R.string.fragment_edit_comment_rating_title_0
}
