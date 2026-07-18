package me.proxer.app.anime.stream

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.anime.resolver.StreamResolver
import me.proxer.app.anime.resolver.StreamResolverFactory
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.ResettingMutableLiveData
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.extension.toMediaLanguage
import me.proxer.library.ProxerApi
import me.proxer.library.entity.anime.Stream
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category

/**
 * A neighbouring episode together with the stream that was successfully resolved for it.
 *
 * [hosterName] is the hoster that actually resolved, which is not necessarily the preferred one —
 * the candidate walk falls through to others when the preferred hoster is absent or unplayable.
 * Carrying it forward keeps the next navigation from re-trying a hoster that already failed.
 */
data class EpisodeNavigationTarget(val episode: Int, val video: StreamResolutionResult.Video, val hosterName: String)

/**
 * Resolves neighbouring episodes for the internal player.
 *
 * Deliberately not a [me.proxer.app.base.BaseViewModel]: that base class models "load this
 * screen's single payload", whereas episode navigation is an on-demand side channel fired by a
 * button press or by playback ending. This mirrors [me.proxer.app.anime.AnimeViewModel.resolve].
 */
class StreamEpisodeViewModel(private val entryId: String, private val language: AnimeLanguage) : ViewModel() {

    private val api by safeInject<ProxerApi>()
    private val storageHelper by safeInject<StorageHelper>()
    private val preferenceHelper by safeInject<PreferenceHelper>()

    val episodeNavigationResult = ResettingMutableLiveData<EpisodeNavigationTarget>()
    val episodeNavigationError = ResettingMutableLiveData<ErrorAction>()
    val isNavigating = MutableLiveData(false)

    private var navigationDisposable: Disposable? = null
    private var bookmarkDisposable: Disposable? = null

    override fun onCleared() {
        navigationDisposable?.dispose()
        bookmarkDisposable?.dispose()

        navigationDisposable = null
        bookmarkDisposable = null

        super.onCleared()
    }

    fun navigateTo(episode: Int, preferredHoster: String?) {
        navigationDisposable?.dispose()

        navigationDisposable =
            api.anime
                .streams(entryId, episode, language)
                .includeProxerStreams(true)
                .buildSingle()
                .map { streams -> orderCandidates(streams, preferredHoster) }
                .flatMap { candidates -> resolveFirstVideo(episode, candidates) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { isNavigating.value = true }
                .doAfterTerminate { isNavigating.value = false }
                .subscribeAndLogErrors(
                    {
                        episodeNavigationError.value = null
                        episodeNavigationResult.value = it
                    },
                    {
                        episodeNavigationResult.value = null
                        episodeNavigationError.value = ErrorUtils.handle(it)
                    },
                )
    }

    fun bookmark(episode: Int) {
        if (!preferenceHelper.areBookmarksAutomatic || !storageHelper.isLoggedIn) return

        bookmarkDisposable?.dispose()
        bookmarkDisposable =
            api.ucp
                .setBookmark(entryId, episode, language.toMediaLanguage(), Category.ANIME)
                .buildSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeAndLogErrors()
    }

    /** A stream paired with the resolver that will be used for it, so the lookup happens once. */
    private data class Candidate(val stream: Stream, val resolver: StreamResolver)

    /**
     * Keeps only streams the internal player could actually use, one per hoster, with
     * [preferredHoster] first. The sort is stable, so the remaining order matches the API's.
     *
     * The login filter has no counterpart in [me.proxer.app.anime.AnimeViewModel.streamSingle],
     * which lists non-public streams and gates them in the UI instead. The player has no such UI,
     * so it filters here; the set of *playable* streams stays the same across both screens.
     */
    private fun orderCandidates(streams: List<Stream>, preferredHoster: String?): List<Candidate> = streams
        .mapNotNull { stream ->
            StreamResolverFactory
                .resolverFor(stream.hosterName)
                ?.takeUnless { resolver -> resolver.ignore }
                ?.let { resolver -> Candidate(stream, resolver) }
        }.filter { candidate -> candidate.stream.isPublic || storageHelper.isLoggedIn }
        .groupBy { candidate -> candidate.stream.hoster }
        .map { (_, group) -> group.first() }
        .sortedByDescending { candidate ->
            candidate.stream.hosterName.equals(preferredHoster, ignoreCase = true)
        }

    /**
     * Resolves [candidates] one at a time and emits the first that yields a playable
     * [StreamResolutionResult.Video]. Non-video results and resolver failures are skipped rather
     * than aborting the walk — a hoster that hands back a Link or an App entry is normal.
     */
    private fun resolveFirstVideo(episode: Int, candidates: List<Candidate>): Single<EpisodeNavigationTarget> =
        Observable
            .fromIterable(candidates)
            .concatMapMaybe { candidate ->
                candidate.resolver
                    .resolve(candidate.stream.id)
                    .flatMapMaybe { result ->
                        when (result) {
                            is StreamResolutionResult.Video ->
                                Maybe.just(
                                    EpisodeNavigationTarget(episode, result, candidate.stream.hosterName),
                                )

                            else -> Maybe.empty()
                        }
                    }.onErrorComplete()
            }.firstElement()
            .switchIfEmpty(Single.error(StreamResolutionException()))
}
