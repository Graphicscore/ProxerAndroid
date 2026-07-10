package me.proxer.app.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject

@Composable
fun AgeConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit = {}) {
    val preferenceHelper = koinInject<PreferenceHelper>()

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.dialog_age_confirmation_content)) },
        confirmButton = {
            TextButton(
                onClick = {
                    preferenceHelper.isAgeRestrictedMediaAllowed = true
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.dialog_age_confirmation_positive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AgeConfirmationDialogPreview() {
    ProxerTheme {
        AgeConfirmationDialog(onDismiss = {})
    }
}
