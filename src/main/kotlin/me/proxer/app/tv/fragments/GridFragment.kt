package me.proxer.app.tv.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.transition.Scene
import io.reactivex.subjects.PublishSubject
import me.proxer.app.R
import androidx.leanback.widget.VerticalGridPresenter


/**
 * @author Graphicscore (Dominik Louven)
 */
open class GridFragment : Fragment(), BrowseSupportFragment.MainFragmentAdapterProvider,
    OnItemViewSelectedListener, OnItemViewClickedListener {

    public val viewSelected: PublishSubject<Int> = PublishSubject.create()

    public val viewClicked: PublishSubject<Int> = PublishSubject.create()

    private var sceneAfterEntranceTransition: Scene? = null

    var adapter: ObjectAdapter? = null
    set(value) {
        field = value
        updateAdapter()
    }

    public var gridPresenter: VerticalGridPresenter? = null
    set(value) {
        if(value != null){
            value.onItemViewSelectedListener = this
            value.onItemViewClickedListener = this
            field = value
        }
    }

    public var gridViewHolder: VerticalGridPresenter.ViewHolder? = null

    private var selectedPosition: Int = -1

    set(value) {
        field = value
        if(gridViewHolder != null && gridViewHolder!!.gridView.adapter != null){
            gridViewHolder!!.gridView.setSelectedPositionSmooth(value)
        }
    }

    private val gridMainFragmentAdapter: BrowseSupportFragment.MainFragmentAdapter<Fragment> = object : BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
        override fun setEntranceTransitionState(state: Boolean) {
            this@GridFragment.setEntranceTransitionState(state)
        }
    }

    private val onChildLaidOutListener: OnChildLaidOutListener = object : OnChildLaidOutListener {
        override fun onChildLaidOut(parent: ViewGroup?, view: View?, position: Int, id: Long) {
            if(position == 0){
                showOrHideTitle()
            }
        }
    }

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> {
        return gridMainFragmentAdapter
    }

    private fun setEntranceTransitionState(state: Boolean){
        gridPresenter?.setEntranceTransitionState(gridViewHolder,state)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gridViewHolder = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gridDock = view.findViewById<ViewGroup>(R.id.browse_grid_dock)
        gridViewHolder = gridPresenter?.onCreateViewHolder(gridDock)
        gridDock.addView(gridViewHolder?.view)
        gridViewHolder?.gridView?.setOnChildLaidOutListener(onChildLaidOutListener)

        sceneAfterEntranceTransition = Scene(gridDock)
        (sceneAfterEntranceTransition as Scene).setEnterAction(Runnable {
            setEntranceTransitionState(true)
        })

        mainFragmentAdapter.fragmentHost.notifyViewCreated(gridMainFragmentAdapter)
        updateAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.tv_grid_fragment, container, false)
    }

    private fun updateAdapter(){
        if(gridViewHolder != null){
            gridPresenter?.onBindViewHolder(gridViewHolder,adapter)
            if(selectedPosition  != -1){
                gridViewHolder?.gridView?.selectedPosition = selectedPosition
            }
        }
    }

    private fun showOrHideTitle(){
        if(gridViewHolder?.gridView?.findViewHolderForAdapterPosition(selectedPosition) != null){
            if (!gridViewHolder?.gridView?.hasPreviousViewInSameRow(selectedPosition)!!) {
                gridMainFragmentAdapter.fragmentHost.showTitleView(true)
            } else {
                gridMainFragmentAdapter.fragmentHost.showTitleView(false)
            }
        }
    }

    override fun onItemSelected(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row?
    ) {
        var positon = gridViewHolder?.gridView?.selectedPosition!!
        Log.v("TV", "grid selected position $positon")
        if(positon != selectedPosition){
            selectedPosition = positon
            showOrHideTitle()
            viewSelected.onNext(positon);
        }
    }

    override fun onItemClicked(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row?
    ) {
        var positon = gridViewHolder?.gridView?.selectedPosition!!
        viewClicked.onNext(positon)
    }
}
