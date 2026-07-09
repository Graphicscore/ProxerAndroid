package me.proxer.app.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun LogoutDialog(onDismiss: () -> Unit) {
    val viewModel = koinViewModel<LogoutViewModel>()

    val isLoading by viewModel.isLoading.observeAsState(false)
    val success by viewModel.success.observeAsState()
    val error by viewModel.error.observeAsState()

    LaunchedEffect(success) {
        if (success != null) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.dialog_logout_content))
                error?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(action.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isLoading == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.logout() },
                enabled = isLoading != true,
            ) {
                Text(stringResource(R.string.dialog_logout_positive))
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
private fun LogoutDialogPreview() {
    ProxerTheme {
        LogoutDialog(onDismiss = {})
    }
}
