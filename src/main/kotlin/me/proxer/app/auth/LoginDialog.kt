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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<LoginViewModel>()

    val isLoading by viewModel.isLoading.observeAsState(false)
    val success by viewModel.success.observeAsState()
    val error by viewModel.error.observeAsState()
    val isTwoFa by viewModel.isTwoFactorAuthenticationEnabled.observeAsState(false)

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(success) {
        if (success != null) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_login_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoading == true) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            usernameError = null
                        },
                        label = { Text(stringResource(R.string.dialog_login_username_hint)) },
                        isError = usernameError != null,
                        supportingText = usernameError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text(stringResource(R.string.dialog_login_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = passwordError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isTwoFa == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = secretKey,
                            onValueChange = { secretKey = it },
                            label = { Text(stringResource(R.string.dialog_login_secret_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    error?.let { action ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(action.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val activity = context as? BaseActivity
                    TextButton(
                        onClick = { activity?.showPage(ProxerUrls.registerWeb()) },
                        enabled = activity != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_login_registration),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val u = username.trim()
                    val p = password.trim()
                    var valid = true
                    if (u.isBlank()) {
                        usernameError = context.getString(R.string.dialog_login_error_username)
                        valid = false
                    }
                    if (p.isBlank()) {
                        passwordError = context.getString(R.string.dialog_login_error_password)
                        valid = false
                    }
                    if (valid) {
                        viewModel.login(u, p, secretKey.trim().ifBlank { null })
                    }
                },
                enabled = isLoading != true,
            ) {
                Text(stringResource(R.string.dialog_login_positive))
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
private fun LoginDialogPreview() {
    ProxerTheme {
        LoginDialog(onDismiss = {})
    }
}
