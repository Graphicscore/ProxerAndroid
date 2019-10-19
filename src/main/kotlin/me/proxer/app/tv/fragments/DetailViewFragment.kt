/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package me.proxer.app.tv.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View

import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter

import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import me.proxer.app.GlideApp
import me.proxer.app.R
import me.proxer.app.media.MediaInfoViewModel

import me.proxer.app.tv.CardPresenterSelector
import me.proxer.app.tv.activity.DetailActivity
import me.proxer.app.tv.presenters.DetailsDescriptionPresenter
import me.proxer.app.tv.presenters.OneLineActionPresenter
import me.proxer.app.util.extension.logErrors
import me.proxer.library.entity.info.Entry
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber


/**
 * Displays a card with more details using a [DetailsSupportFragment].
 */
class DetailViewFragment : DetailsSupportFragment(), OnItemViewClickedListener, OnItemViewSelectedListener {

    companion object {
        private const val ACTION_WATCH: Long = 1
        //private static final long ACTION_WISHLIST = 2;
        private const val ACTION_SIMILIAR: Long = 2

        fun newInstance(entry: Entry) : DetailViewFragment {
            val fragment = DetailViewFragment()
            fragment.data = entry
            return fragment
        }
    }

    private var mActionWatch: Action? = null
    //private Action mActionWishList;
    private var mActionSimiliar: Action? = null
    private var mRowsAdapter: ArrayObjectAdapter? = null
    private val mDetailsBackground = DetailsSupportFragmentBackgroundController(this)

    private lateinit var data : Entry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        setupEventListeners()
    }

    private fun setupUi() {
        // Load the card we want to display from a JSON resource. This JSON data could come from
        // anywhere in a real world app, e.g. a server.
        //val extras = activity!!.intent.extras
        //extras!!.getParcelable<Anime>(EXTRA_ANIME)
        // Setup fragment
        //setTitle(getString(R.string.detail_view_title));

        val rowPresenter = object : FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter(activity!!)) {

            override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
                // Customize Actionbar and Content by using custom colors.
                val viewHolder = super.createRowViewHolder(parent)

                /*val actionsView = viewHolder.view.findViewById<View>(R.id.details_overview_actions_background)
                actionsView.setBackgroundColor(activity!!.resources.getColor(R.color.detail_view_actionbar_background))

                val detailsView = viewHolder.view.findViewById<View>(R.id.details_frame)
                detailsView.setBackgroundColor(
                    resources.getColor(R.color.detail_view_background))*/
                return viewHolder
            }
        }

        /*val mHelper = FullWidthDetailsOverviewSharedElementHelper()
        mHelper.setSharedElementEnterTransition(activity, TRANSITION_NAME)
        rowPresenter.setListener(mHelper)*/
        rowPresenter.isParticipatingEntranceTransition = false
        prepareEntranceTransition()

        val shadowDisabledRowPresenter = ListRowPresenter()
        shadowDisabledRowPresenter.shadowEnabled = false

        // Setup PresenterSelector to distinguish between the different rows.
        val rowPresenterSelector = ClassPresenterSelector()
        rowPresenterSelector.addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
        //rowPresenterSelector.addClassPresenter(CardListRow.class, shadowDisabledRowPresenter);
        rowPresenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
        mRowsAdapter = ArrayObjectAdapter(rowPresenterSelector)

        // Setup action and detail row.
        val detailsOverview = DetailsOverviewRow(data)

        GlideApp.with(this)
            .load(ProxerUrls.entryImage(data.id).toString())
            .logErrors()
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                }

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    detailsOverview.imageDrawable = resource
                }
            })


        val actionAdapter = ArrayObjectAdapter(OneLineActionPresenter())

        mActionWatch = Action(ACTION_WATCH, "WATCH")//getString(R.string.action_watch))
        //mActionWishList = new Action(ACTION_WISHLIST, getString(R.string.action_wishlist));
        mActionSimiliar = Action(ACTION_SIMILIAR, "SIMILIAR")//getString(R.string.action_similiar))

        actionAdapter.add(mActionWatch)
        //actionAdapter.add(mActionWishList);
        actionAdapter.add(mActionSimiliar)

        detailsOverview.actionsAdapter = actionAdapter

        mRowsAdapter!!.add(detailsOverview)

        // Setup related row.
        // we dont need this right now
        /*ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(
                new CardPresenterSelector(getActivity()));
        for (Card characterCard : data.getCharacters()) listRowAdapter.add(characterCard);
        HeaderItem header = new HeaderItem(0, getString(R.string.header_related));
        mRowsAdapter.add(new CardListRow(header, listRowAdapter, null));*/

        // Setup recommended row.
        val listRowAdapter = ArrayObjectAdapter(CardPresenterSelector(activity!!))
        //for (Card card : data.getRecommended()) listRowAdapter.add(card);
        val header = HeaderItem(0, "SIMILIAR_HEADER")//getString(R.string.header_similar))
        mRowsAdapter!!.add(ListRow(header, listRowAdapter))

        adapter = mRowsAdapter!!
        Handler().postDelayed({ startEntranceTransition() }, 500)
        initializeBackground()
    }

    private fun initializeBackground() {
        mDetailsBackground.enableParallax()
        /*mDetailsBackground.setCoverBitmap(BitmapFactory.decodeResource(getResources(),
                R.drawable.background_canyon));*/
    }

    private fun setupEventListeners() {
        //setOnItemViewSelectedListener(this)
        onItemViewClickedListener = this
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                               rowViewHolder: RowPresenter.ViewHolder, row: Row) {
        if (item !is Action) return

        if (item.id == ACTION_SIMILIAR) {
            setSelectedPosition(1)
        } else {

            Toast.makeText(activity, "Clicked Action", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder, item: Any,
                                rowViewHolder: RowPresenter.ViewHolder, row: Row) {
        if (mRowsAdapter!!.indexOf(row) > 0) {
            val backgroundColor = Color.YELLOW//resources.getColor(R.color.detail_view_related_background)
            view!!.setBackgroundColor(backgroundColor)
        } else {
            view!!.background = null
        }
    }


}
