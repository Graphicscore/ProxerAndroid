package me.proxer.app.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.getSystemService
import com.mikepenz.aboutlibraries.LibsBuilder
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.BuildConfig
import me.proxer.app.R
import me.proxer.app.settings.status.ServerStatusActivity
import me.proxer.app.util.extension.toast

private const val SUPPORT_PROXER_MAIL = "appsupport@proxer.de"
private const val DEVELOPER_GITHUB_NAME = "rubengees"

private const val TEAM_URL = "https://proxer.me/team?device=default"
private const val FACEBOOK_URL = "https://facebook.com/Anime.Proxer.Me"
private const val TWITTER_URL = "https://twitter.com/proxerme"
private const val YOUTUBE_URL = "https://youtube.com/channel/UC7h-fT9Y9XFxuZ5GZpbcrtA"
private const val DISCORD_URL = "https://discord.gg/XwrEDmA"
private const val REPOSITORY_URL = "https://github.com/proxer/ProxerAndroid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_info)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Info section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.app_name)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_version_title)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val title = context.getString(R.string.clipboard_title)
                        context.getSystemService<ClipboardManager>()?.setPrimaryClip(
                            ClipData.newPlainText(title, BuildConfig.VERSION_NAME),
                        )
                        context.toast(R.string.clipboard_status)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_licenses_title)) },
                    supportingContent = { Text(stringResource(R.string.about_licenses_description)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        LibsBuilder()
                            .withActivityTitle(context.getString(R.string.about_licenses_activity_title))
                            .start(context as Activity)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_source_code)) },
                    supportingContent = { Text(stringResource(R.string.about_source_code_description)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_server_status)) },
                    supportingContent = { Text(stringResource(R.string.about_server_status_description)) },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    modifier = Modifier.clickable { ServerStatusActivity.navigateTo(context as Activity) },
                )
            }

            item { HorizontalDivider() }

            // --- Social Media section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_social_media_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_facebook_title)) },
                    supportingContent = { Text(stringResource(R.string.about_facebook_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FACEBOOK_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_twitter_title)) },
                    supportingContent = { Text(stringResource(R.string.about_twitter_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TWITTER_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_youtube_title)) },
                    supportingContent = { Text(stringResource(R.string.about_youtube_description)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_URL)))
                    },
                )
            }
            item {
                // Discord: no icon (CommunityMaterial.Icon.cmd_discord was removed in 7.x)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_discord_title)) },
                    supportingContent = { Text(stringResource(R.string.about_discord_description)) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL)))
                    },
                )
            }

            item { HorizontalDivider() }

            // --- Support section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_support_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_support_info)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEAM_URL)))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_support_mail_title)) },
                    supportingContent = { Text(stringResource(R.string.about_support_mail_description)) },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_PROXER_MAIL))
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_support_mail_subject))
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            context.toast(R.string.about_error_mail_no_activity)
                        }
                    },
                )
            }

            item { HorizontalDivider() }

            // --- Developer section ---
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_developer_title)) })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_developer_github_title)) },
                    supportingContent = { Text(DEVELOPER_GITHUB_NAME) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$DEVELOPER_GITHUB_NAME")))
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    ProxerTheme {
        AboutScreen()
    }
}
