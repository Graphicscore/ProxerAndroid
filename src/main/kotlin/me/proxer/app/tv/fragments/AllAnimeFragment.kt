package me.proxer.app.tv.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding3.view.clicks
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.proxer.app.R
import me.proxer.app.media.LocalTag
import me.proxer.app.media.list.MediaListViewModel
import me.proxer.app.tv.CardPresenterSelector
import me.proxer.app.tv.TVMainActivity
import me.proxer.app.tv.activity.DetailActivity
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.unsafeParametersOf
import me.proxer.library.ProxerApi
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.enums.*
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
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
                    Timber.e("Unknown Error")
                }
                else -> {
                    showError(it)
                    Timber.e(String.format("Error : %s",it.message))
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

        viewSelected
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(this.scope())
            .subscribe {
                if(it > 0){
                    var percent: Float = it.toFloat() / (adapter as ArrayObjectAdapter).size().toFloat()
                    if(percent >= 0.7F) {
                        if (!viewModel.isLoading.value!!) {
                            viewModel.load()
                        }
                    }
                }
            }

        viewClicked
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(this.scope())
            .subscribe {
                var intent = Intent(context,DetailActivity::class.java)
                intent.putExtra(DetailActivity.ID_EXTRA,((adapter as ArrayObjectAdapter).get(it.first) as MediaListEntry).id)
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity!!,
                    (it.second.view as ImageCardView).mainImageView,
                    "DetailActivity"
                ).toBundle()
                startActivity(intent,bundle)
            }

    }

    private fun showError(action: ErrorUtils.ErrorAction) {
        val errorFragment = ErrorFragment.newInstance(activity as TVMainActivity,viewModel, action)
        activity?.supportFragmentManager?.beginTransaction()
            ?.replace(R.id.root,errorFragment)
            ?.addToBackStack(errorFragment.javaClass.name)
            ?.commit()
    }

    private fun hideData(){
        Timber.d("Hide Data")
    }

    private fun showData(mediaList: List<MediaListEntry>?) {
        Timber.d(String.format("ShowData %s", mediaList?.size))
        (adapter as ArrayObjectAdapter).setItems(viewModel.data.value,null);
    }
}
