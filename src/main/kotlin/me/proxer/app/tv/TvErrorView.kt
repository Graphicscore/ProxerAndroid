package me.proxer.app.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject

@Composable
fun TvErrorView(
    error: ErrorUtils.ErrorAction,
    onLoginClick: (() -> Unit)? = null,
    onRetryClick: () -> Unit
) {
    val preferenceHelper: PreferenceHelper = koinInject()
    var showAgeConfirmDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(error.message),
            color = MaterialTheme.colorScheme.error
        )
        when (error.buttonAction) {
            ButtonAction.AGE_CONFIRMATION -> {
                OutlinedButton(onClick = { showAgeConfirmDialog = true }) {
                    Text(stringResource(error.buttonMessage))
                }
            }
            ButtonAction.LOGIN -> {
                OutlinedButton(onClick = { onLoginClick?.invoke() }) {
                    Text(stringResource(error.buttonMessage))
                }
            }
            else -> {
                if (error.buttonMessage != ACTION_MESSAGE_HIDE) {
                    OutlinedButton(onClick = onRetryClick) {
                        Text(
                            stringResource(
                                if (error.buttonMessage == ACTION_MESSAGE_DEFAULT) R.string.error_action_retry
                                else error.buttonMessage
                            )
                        )
                    }
                }
            }
        }
    }

    if (showAgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAgeConfirmDialog = false },
            text = { Text(stringResource(R.string.dialog_age_confirmation_content)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferenceHelper.isAgeRestrictedMediaAllowed = true
                        showAgeConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_age_confirmation_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgeConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
