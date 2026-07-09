package me.proxer.app.profile.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.view.ProxerWebView
import me.proxer.app.util.extension.toAppString
import me.proxer.library.entity.user.UserAbout
import me.proxer.library.enums.Gender
import me.proxer.library.enums.RelationshipStatus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileAboutScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<ProfileAboutViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    ContentScreen(
        isLoading = isLoading == true,
        error = error,
        onRetry = { viewModel.load() },
    ) {
        if (data != null) {
            ProfileAboutBody(about = data!!)
        }
    }
}

@Composable
private fun ProfileAboutBody(about: UserAbout) {
    val context = LocalContext.current

    val normalizedGender = when (about.gender) {
        Gender.UNKNOWN -> null
        else -> about.gender.toAppString(context)
    }
    val normalizedRelationshipStatus = when (about.relationshipStatus) {
        RelationshipStatus.UNKNOWN -> null
        else -> about.relationshipStatus.toAppString(context)
    }
    val normalizedBirthday = if (about.birthday == "0000-00-00") {
        null
    } else {
        about.birthday.split("-").let { parts ->
            if (parts.size == 3) {
                val (year, month, day) = parts
                "$day.$month.$year"
            } else {
                null
            }
        }
    }

    val fields = buildList {
        if (about.occupation.isNotBlank()) add(context.getString(R.string.fragment_about_occupation) to about.occupation)
        if (about.interests.isNotBlank()) add(context.getString(R.string.fragment_about_interests) to about.interests)
        if (about.city.isNotBlank()) add(context.getString(R.string.fragment_about_city) to about.city)
        if (about.country.isNotBlank()) add(context.getString(R.string.fragment_about_country) to about.country)
        if (normalizedGender != null) add(context.getString(R.string.fragment_about_gender) to normalizedGender)
        if (normalizedRelationshipStatus != null) add(context.getString(R.string.fragment_about_relationship_status) to normalizedRelationshipStatus)
        if (normalizedBirthday != null) add(context.getString(R.string.fragment_about_birthday) to normalizedBirthday)
        if (about.website.isNotBlank()) add(context.getString(R.string.fragment_about_website) to about.website)
        if (about.facebook.isNotBlank()) add(context.getString(R.string.fragment_about_facebook) to about.facebook)
        if (about.youtube.isNotBlank()) add(context.getString(R.string.fragment_about_youtube) to about.youtube)
        if (about.chatango.isNotBlank()) add(context.getString(R.string.fragment_about_chatango) to about.chatango)
        if (about.twitter.isNotBlank()) add(context.getString(R.string.fragment_about_twitter) to about.twitter)
        if (about.skype.isNotBlank()) add(context.getString(R.string.fragment_about_skype) to about.skype)
        if (about.deviantart.isNotBlank()) add(context.getString(R.string.fragment_about_deviantart) to about.deviantart)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (fields.isNotEmpty()) {
            Text(
                text = context.getString(R.string.fragment_about_general),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            fields.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.4f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        }

        if (about.about.isNotBlank()) {
            if (fields.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Text(
                text = context.getString(R.string.fragment_about_about),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val aboutHtml = about.about
            AndroidView(
                factory = { ctx -> ProxerWebView(ctx) },
                update = { view -> view.loadHtml(aboutHtml) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
