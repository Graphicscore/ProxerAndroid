package me.proxer.app.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.NavigationDrawer
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.util.extension.startActivity
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvAppShell(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit
) {
    val viewModel: TvShellViewModel = koinViewModel()
    val user by viewModel.user.observeAsState()
    val logoutError by viewModel.logoutError.observeAsState()
    val isLoggingOut by viewModel.isLoggingOut.observeAsState()
    val context = LocalContext.current

    var currentSection by remember { mutableStateOf(TvSection.ANIME) }

    LaunchedEffect(logoutError) {
        if (logoutError != null) {
            Toast.makeText(context, context.getString(logoutError!!.message), Toast.LENGTH_SHORT).show()
        }
    }

    NavigationDrawer(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        drawerContent = { drawerValue ->
            TvNavigationDrawerContent(
                currentSection = currentSection,
                user = user,
                drawerValue = drawerValue,
                onSectionSelected = { currentSection = it },
                onLoginClick = { context.startActivity<TvLoginActivity>() },
                onLogoutClick = { if (isLoggingOut != true) viewModel.logout() }
            )
        }
    ) {
        when (currentSection) {
            TvSection.ANIME -> TvBrowseScreen(
                onMediaClick = onMediaClick,
                onSearchClick = onSearchClick
            )
            TvSection.NEWS -> TvPlaceholderScreen("News")
            TvSection.BOOKMARKS -> TvPlaceholderScreen("Bookmarks")
            TvSection.SCHEDULE -> TvPlaceholderScreen("Schedule")
            TvSection.INFO -> TvPlaceholderScreen("Info")
            TvSection.SETTINGS -> TvPlaceholderScreen("Settings")
        }
    }
}
