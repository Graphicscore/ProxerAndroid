package me.proxer.app.media.list

import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import me.proxer.app.R
import me.proxer.app.ui.view.ExpandableSelectionView
import me.proxer.app.util.extension.ProxerLibExtensions
import me.proxer.app.util.extension.dip
import me.proxer.app.util.extension.enableLayoutAnimationsSafely
import me.proxer.app.util.extension.enumSetOf
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toAppStringDescription
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.Language
import me.proxer.library.enums.TagRateFilter
import me.proxer.library.enums.TagSpoilerFilter

/**
 * @author Ruben Gees
 */
class MediaListSearchBottomSheet private constructor(
    private val fragment: MediaListFragment,
    private val viewModel: MediaListViewModel
) {

    companion object {
        fun bindTo(
            fragment: MediaListFragment,
            viewModel: MediaListViewModel
        ) = MediaListSearchBottomSheet(fragment, viewModel)
    }

    private val bottomSheetBehaviour = BottomSheetBehavior.from(fragment.searchBottomSheet)

    init {
        fragment.languageSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()
        fragment.genreSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()
        fragment.excludedGenreSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()
        fragment.fskSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()
        fragment.tagSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()
        fragment.excludedTagSelector.findViewById<ViewGroup>(R.id.items).enableLayoutAnimationsSafely()

        initClickSubscriptions()
        initSelectionSubscriptions()

        fragment.includeUnratedTags.checkedChanges()
            .skipInitialValue()
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribe { fragment.tagRateFilter = if (it) TagRateFilter.ALL else TagRateFilter.RATED_ONLY }

        fragment.includeSpoilerTags.checkedChanges()
            .skipInitialValue()
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribe { fragment.tagSpoilerFilter = if (it) TagSpoilerFilter.ALL else TagSpoilerFilter.NO_SPOILERS }

        fragment.hideFinishedCheckBox.checkedChanges()
            .skipInitialValue()
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribe { fragment.hideFinished = it }

        val fskItems = FskConstraint.values().map {
            ExpandableSelectionView.Item(
                it.toAppString(fragment.requireContext()),
                it.toAppStringDescription(fragment.requireContext())
            )
        }

        val languageItems = listOf(
            fragment.getString(R.string.fragment_media_list_all_languages),
            fragment.getString(R.string.language_german),
            fragment.getString(R.string.language_english)
        )

        fragment.languageSelector.simpleItems = languageItems
        fragment.fskSelector.items = fskItems

        fragment.searchBottomSheetTitle.doOnLayout {
            if (fragment.view != null) {
                bottomSheetBehaviour.peekHeight = fragment.searchBottomSheetTitle.height + fragment.dip(10)
                fragment.searchBottomSheet.isVisible = true
            }
        }

        viewModel.loadTags()

        viewModel.genreData.observe(fragment.viewLifecycleOwner, Observer {
            if (it != null) {
                val items = it.map { tag -> ExpandableSelectionView.Item(tag.name, tag.description) }

                fragment.genreSelector.items = items
                fragment.excludedGenreSelector.items = items

                fragment.genreSelector.isVisible = true
                fragment.excludedGenreSelector.isVisible = true
            }
        })

        viewModel.tagData.observe(fragment.viewLifecycleOwner, Observer {
            if (it != null) {
                val items = it.map { tag -> ExpandableSelectionView.Item(tag.name, tag.description) }

                fragment.tagSelector.items = items
                fragment.excludedTagSelector.items = items

                fragment.tagSelector.isVisible = true
                fragment.excludedTagSelector.isVisible = true
            }
        })
    }

    private fun initClickSubscriptions() {
        fragment.searchBottomSheetTitle.clicks()
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribe {
                bottomSheetBehaviour.state = when (bottomSheetBehaviour.state) {
                    STATE_EXPANDED -> STATE_COLLAPSED
                    else -> STATE_EXPANDED
                }
            }

        fragment.search.clicks()
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribe {
                fragment.searchView.clearFocus()

                bottomSheetBehaviour.state = STATE_COLLAPSED

                viewModel.reload()
            }
    }

    private fun initSelectionSubscriptions() {
        fragment.languageSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors {
                fragment.language = when {
                    it.firstOrNull() == fragment.getString(R.string.language_german) -> Language.GERMAN
                    it.firstOrNull() == fragment.getString(R.string.language_english) -> Language.ENGLISH
                    else -> null
                }
            }

        fragment.genreSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors { selections ->
                viewModel.genreData.value?.let { genreData ->
                    fragment.genres = selections.mapNotNull { selection -> genreData.find { it.name == selection } }
                }
            }

        fragment.excludedGenreSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors { selections ->
                viewModel.genreData.value?.let { genreData ->
                    fragment.excludedGenres = selections.mapNotNull { selection ->
                        genreData.find { it.name == selection }
                    }
                }
            }

        fragment.fskSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors { selections ->
                fragment.fskConstraints = enumSetOf(selections.map {
                    ProxerLibExtensions.fskConstraintFromAppString(fragment.requireContext(), it)
                })
            }

        fragment.tagSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors { selections ->
                viewModel.tagData.value?.let { tagData ->
                    fragment.tags = selections.mapNotNull { selection -> tagData.find { it.name == selection } }
                }
            }

        fragment.excludedTagSelector.selectionChangeSubject
            .autoDisposable(fragment.viewLifecycleOwner.scope())
            .subscribeAndLogErrors { selections ->
                viewModel.tagData.value?.let { tagData ->
                    fragment.excludedTags = selections.mapNotNull { selection -> tagData.find { it.name == selection } }
                }
            }
    }

    fun onBackPressed() = if (bottomSheetBehaviour.state != STATE_COLLAPSED) {
        bottomSheetBehaviour.state = STATE_COLLAPSED

        true
    } else {
        false
    }
}
