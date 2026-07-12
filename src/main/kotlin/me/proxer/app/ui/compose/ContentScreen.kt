package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.auth.LoginDialog
import me.proxer.app.base.BaseActivity
import me.proxer.app.settings.AgeConfirmationDialog
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    isLoading: Boolean,
    error: ErrorAction?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isSwipeToRefreshEnabled: Boolean = false,
    onRefresh: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAgeConfirmationDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginDialog(onDismiss = { showLoginDialog = false })
    }
    if (showAgeConfirmationDialog) {
        AgeConfirmationDialog(onDismiss = { showAgeConfirmationDialog = false })
    }

    PullToRefreshBox(
        isRefreshing = isLoading && isSwipeToRefreshEnabled,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading && !isSwipeToRefreshEnabled -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(error.message))
                        if (error.buttonMessage != ACTION_MESSAGE_HIDE) {
                            Button(
                                onClick = {
                                    when (error.buttonAction) {
                                        ButtonAction.LOGIN -> showLoginDialog = true

                                        ButtonAction.AGE_CONFIRMATION -> showAgeConfirmationDialog = true

                                        ButtonAction.CAPTCHA,
                                        ButtonAction.NETWORK_SETTINGS,
                                        ButtonAction.OPEN_LINK,
                                        -> {
                                            (context as? BaseActivity)?.let { activity ->
                                                error.toClickListener(activity)?.invoke()
                                            }
                                        }

                                        else -> onRetry()
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                val label = when (error.buttonMessage) {
                                    ACTION_MESSAGE_DEFAULT -> stringResource(R.string.error_action_retry)
                                    else -> stringResource(error.buttonMessage)
                                }
                                Text(label)
                            }
                        }
                    }
                }
            }

            else -> content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenLoadingPreview() {
    ProxerTheme {
        ContentScreen(isLoading = true, error = null, onRetry = {}) {
            Text("Content")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenErrorPreview() {
    ProxerTheme {
        ContentScreen(
            isLoading = false,
            error = ErrorAction(R.string.error_unknown),
            onRetry = {},
        ) {
            Text("Content")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenContentPreview() {
    ProxerTheme {
        ContentScreen(isLoading = false, error = null, onRetry = {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Content loaded!")
            }
        }
    }
}
