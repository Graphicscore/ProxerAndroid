package me.proxer.app.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import me.proxer.app.auth.LocalUser
import me.proxer.library.util.ProxerUrls

@Composable
fun NavigationDrawerScope.TvNavigationDrawerContent(
    currentSection: TvSection,
    user: LocalUser?,
    drawerValue: DrawerValue,
    onSectionSelected: (TvSection) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp),
    ) {
        // Profile header as a NavigationDrawerItem
        if (user != null) {
            NavigationDrawerItem(
                selected = false,
                onClick = onLogoutClick,
                leadingContent = {
                    if (user.image.isNotBlank()) {
                        AsyncImage(
                            model = ProxerUrls.userImage(user.image).toString(),
                            contentDescription = user.name,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF3B82F6),
                        )
                    }
                },
                supportingContent = {
                    AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                        Text("Sign Out", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                },
            ) {
                AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                    Text(user.name, fontSize = 13.sp)
                }
            }
        } else {
            NavigationDrawerItem(
                selected = false,
                onClick = onLoginClick,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Sign In",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Gray,
                    )
                },
            ) {
                AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                    Text("Sign In", color = Color(0xFF3B82F6), fontSize = 13.sp)
                }
            }
        }

        // Main nav items
        TvNavItem(TvSection.ANIME, "Anime", Icons.Default.Tv, currentSection, drawerValue, onSectionSelected)
        TvNavItem(TvSection.NEWS, "News", Icons.Default.Newspaper, currentSection, drawerValue, onSectionSelected)
        TvNavItem(
            TvSection.BOOKMARKS,
            "Bookmarks",
            Icons.Default.Bookmarks,
            currentSection,
            drawerValue,
            onSectionSelected,
        )
        TvNavItem(
            TvSection.SCHEDULE,
            "Schedule",
            Icons.Default.DateRange,
            currentSection,
            drawerValue,
            onSectionSelected,
        )

        Spacer(Modifier.weight(1f))

        // Footer items
        TvNavItem(TvSection.INFO, "Info", Icons.Default.Info, currentSection, drawerValue, onSectionSelected)
        TvNavItem(
            TvSection.SETTINGS,
            "Settings",
            Icons.Default.Settings,
            currentSection,
            drawerValue,
            onSectionSelected,
        )
    }
}

@Composable
private fun NavigationDrawerScope.TvNavItem(
    section: TvSection,
    label: String,
    icon: ImageVector,
    currentSection: TvSection,
    drawerValue: DrawerValue,
    onSectionSelected: (TvSection) -> Unit,
) {
    NavigationDrawerItem(
        selected = currentSection == section,
        onClick = { onSectionSelected(section) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = label)
        },
    ) {
        AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Logged out")
@Composable
private fun TvNavigationDrawerContentLoggedOutPreview() {
    TvTheme {
        NavigationDrawer(
            drawerContent = { drawerValue ->
                TvNavigationDrawerContent(
                    currentSection = TvSection.ANIME,
                    user = null,
                    drawerValue = drawerValue,
                    onSectionSelected = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                )
            },
        ) {}
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Logged in")
@Composable
private fun TvNavigationDrawerContentLoggedInPreview() {
    TvTheme {
        NavigationDrawer(
            drawerContent = { drawerValue ->
                TvNavigationDrawerContent(
                    currentSection = TvSection.BOOKMARKS,
                    user = LocalUser(token = "", id = "1", name = "Asteria", image = ""),
                    drawerValue = drawerValue,
                    onSectionSelected = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                )
            },
        ) {}
    }
}
