package me.proxer.app.anime.resolver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import me.proxer.app.anime.stream.StreamActivity
import me.proxer.app.base.CustomTabsAware
import me.proxer.app.util.extension.addReferer
import me.proxer.app.util.extension.androidUri
import me.proxer.library.enums.AnimeLanguage
import okhttp3.HttpUrl

/**
 * Everything the internal player needs to know about the episode it is playing, beyond the
 * resolved video URL itself. Passed as intent extras by [StreamResolutionResult.Video.makeIntent].
 */
data class AnimeStreamContext(
    val id: String,
    val name: String?,
    val episode: Int,
    val episodeAmount: Int,
    val language: AnimeLanguage,
    val coverUri: Uri?,
    val hosterName: String?,
)

/**
 * @author Ruben Gees
 */
sealed class StreamResolutionResult {
    class Video(
        url: HttpUrl,
        mimeType: String,
        referer: String? = null,
        adTag: Uri? = null,
        internalPlayerOnly: Boolean = false,
    ) : StreamResolutionResult() {
        companion object {
            const val ID_EXTRA = "id"
            const val NAME_EXTRA = "name"
            const val EPISODE_EXTRA = "episode"
            const val EPISODE_AMOUNT_EXTRA = "episode_amount"
            const val HOSTER_NAME_EXTRA = "hoster_name"
            const val LANGUAGE_EXTRA = "language"
            const val COVER_EXTRA = "cover"
            const val REFERER_EXTRA = "referer"
            const val AD_TAG_EXTRA = "ad_tag"
            const val INTERNAL_PLAYER_ONLY_EXTRA = "internal_player_only"
        }

        private val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(url.androidUri(), mimeType)
                .apply { if (referer != null) putExtra(REFERER_EXTRA, referer) }
                .putExtra(AD_TAG_EXTRA, adTag)
                .putExtra(INTERNAL_PLAYER_ONLY_EXTRA, internalPlayerOnly)
                .addReferer()

        fun makeIntent(
            context: Context,
            streamContext: AnimeStreamContext? = null,
            forceInternal: Boolean = false,
        ): Intent = intent
            .apply { if (forceInternal) component = ComponentName(context, StreamActivity::class.java) }
            .apply {
                if (streamContext != null) {
                    putExtra(ID_EXTRA, streamContext.id)
                    putExtra(EPISODE_EXTRA, streamContext.episode)
                    putExtra(EPISODE_AMOUNT_EXTRA, streamContext.episodeAmount)
                    putExtra(LANGUAGE_EXTRA, streamContext.language)

                    if (streamContext.name != null) putExtra(NAME_EXTRA, streamContext.name)
                    if (streamContext.coverUri != null) putExtra(COVER_EXTRA, streamContext.coverUri)
                    if (streamContext.hosterName != null) putExtra(HOSTER_NAME_EXTRA, streamContext.hosterName)
                }
            }

        fun play(context: Context, streamContext: AnimeStreamContext? = null, forceInternal: Boolean = false) {
            context.startActivity(makeIntent(context, streamContext, forceInternal))
        }
    }

    class Link(private val url: HttpUrl) : StreamResolutionResult() {
        fun show(customTabsAware: CustomTabsAware) {
            customTabsAware.showPage(url, skipCheck = true)
        }

        fun makeIntent(): Intent = Intent(Intent.ACTION_VIEW, url.androidUri())
    }

    class App(uri: Uri) : StreamResolutionResult() {
        private val intent =
            Intent(Intent.ACTION_VIEW)
                .setData(uri)
                .addReferer()

        fun navigate(context: Context) {
            context.startActivity(intent)
        }
    }

    class Message(val message: CharSequence) : StreamResolutionResult()
}
