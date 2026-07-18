package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A search input sized to sit in a [ProxerTopAppBar]'s title slot.
 *
 * A bare `TextField` cannot be used there. Its defaults are keyed to the surface roles — the
 * container is `surfaceContainerHighest` and, worse, the cursor and focused indicator are
 * `colorScheme.primary`. On the accent-colored bar that paints primary on primary, so the caret
 * vanishes while the user types. Everything here is therefore re-keyed to `onPrimary`, and the
 * container is made transparent so the field reads as part of the bar instead of a block floating
 * on it.
 */
@Composable
fun ProxerTopAppBarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            cursorColor = onPrimary,
            focusedTextColor = onPrimary,
            unfocusedTextColor = onPrimary,
            focusedIndicatorColor = onPrimary,
            unfocusedIndicatorColor = onPrimary.copy(alpha = INACTIVE_ALPHA),
            focusedPlaceholderColor = onPrimary.copy(alpha = INACTIVE_ALPHA),
            unfocusedPlaceholderColor = onPrimary.copy(alpha = INACTIVE_ALPHA),
            selectionColors = TextSelectionColors(
                handleColor = onPrimary,
                backgroundColor = onPrimary.copy(alpha = SELECTION_ALPHA),
            ),
        ),
    )
}

private const val INACTIVE_ALPHA = 0.7f
private const val SELECTION_ALPHA = 0.4f
