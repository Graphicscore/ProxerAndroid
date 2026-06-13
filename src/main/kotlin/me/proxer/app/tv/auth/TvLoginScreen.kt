package me.proxer.app.tv.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.proxer.app.auth.LoginViewModel
import me.proxer.app.tv.TvTheme
import me.proxer.app.util.ErrorUtils
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvLoginScreenContent(
    username: String,
    password: String,
    secretKey: String,
    isLoading: Boolean,
    isTwoFa: Boolean,
    error: ErrorUtils.ErrorAction?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(400.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sign In to Proxer", fontSize = 28.sp)

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isTwoFa) ImeAction.Next else ImeAction.Done,
                    ),
                enabled = !isLoading,
            )

            if (isTwoFa) {
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = onSecretKeyChange,
                    label = { Text("OTP Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    enabled = !isLoading,
                )
            }

            error?.let { errorAction ->
                Text(
                    text = stringResource(errorAction.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onLogin,
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Login")
                }
            }
        }
    }
}

@Composable
fun TvLoginScreen(onLoginSuccess: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val success by viewModel.success.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val isTwoFa by viewModel.isTwoFactorAuthenticationEnabled.observeAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success != null) onLoginSuccess()
    }

    TvLoginScreenContent(
        username = username,
        password = password,
        secretKey = secretKey,
        isLoading = isLoading == true,
        isTwoFa = isTwoFa == true,
        error = error,
        onUsernameChange = { username = it },
        onPasswordChange = { password = it },
        onSecretKeyChange = { secretKey = it },
        onLogin = {
            viewModel.login(
                username,
                password,
                if (isTwoFa == true) secretKey.takeIf { it.isNotBlank() } else null,
            )
        },
    )
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvLoginScreenContentIdlePreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "Asteria",
            password = "",
            secretKey = "",
            isLoading = false,
            isTwoFa = false,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvLoginScreenContentLoadingPreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "Asteria",
            password = "secret",
            secretKey = "",
            isLoading = true,
            isTwoFa = false,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvLoginScreenContent2FaPreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "Asteria",
            password = "secret",
            secretKey = "",
            isLoading = false,
            isTwoFa = true,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}
