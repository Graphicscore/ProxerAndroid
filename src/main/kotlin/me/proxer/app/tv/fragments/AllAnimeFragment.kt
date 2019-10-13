package me.proxer.app.tv.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Observer
import me.proxer.app.media.LocalTag
import me.proxer.app.media.list.MediaListViewModel
import me.proxer.app.tv.CardPresenterSelector
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.unsafeParametersOf
import me.proxer.library.ProxerApi
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.enums.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*

/**
 * @author Graphicscore (Dominik Louven)
 */
class AllAnimeFragment : GridFragment()
{
    companion object {
        val NUMBER_OF_COLUMS = 6
        val ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL
    }

    protected val api by safeInject<ProxerApi>()

    val viewModel by viewModel<MediaListViewModel> {
        unsafeParametersOf(
            MediaSearchSortCriteria.RATING, MediaType.ALL_ANIME, null, null, emptyList<LocalTag>(), emptyList<LocalTag>(), EnumSet.noneOf(FskConstraint::class.java),
            emptyList<LocalTag>(), emptyList<LocalTag>(), TagRateFilter.RATED_ONLY, TagSpoilerFilter.NO_SPOILERS, false
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.error.observe(viewLifecycleOwner, Observer {
            when (it) {
                null -> {
                    //hideError()
                    Log.e("TV","Unknown Error")
                }
                else -> {
                    //showError(it)
                    Log.e("TV",String.format("Error : %s",it.message))
                }
            }
        })

        viewModel.data.observe(viewLifecycleOwner, Observer {
            when (it) {
                null -> hideData()
                else -> showData(it)
            }
        })

        viewModel.isLoading.observe(viewLifecycleOwner, Observer {
            //progress.isEnabled = it == true || isSwipeToRefreshEnabled
            //progress.isRefreshing = it == true
        })

        if (viewModel.isLoading.value != true && viewModel.data.value == null && viewModel.error.value == null) {
            viewModel.load()
        }
    }

    private fun setupUI(){
        gridPresenter = VerticalGridPresenter(ZOOM_FACTOR)
        gridPresenter!!.numberOfColumns = NUMBER_OF_COLUMS
        gridPresenter!!.onItemViewSelectedListener

        adapter = ArrayObjectAdapter(CardPresenterSelector(context!!))
    }

    private fun hideData(){
        Log.d("TV","Hide Data")
    }

    private fun showData(mediaList: List<MediaListEntry>?) {
        Log.d("TV", String.format("ShowData %s", mediaList?.size))
        (adapter as ArrayObjectAdapter).setItems(viewModel.data.value,null);
    }
}
