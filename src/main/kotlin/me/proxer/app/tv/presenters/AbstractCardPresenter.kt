package me.proxer.app.tv.presenters


import android.content.Context
import android.util.Log
import android.view.View
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import androidx.lifecycle.ViewModel

import android.view.ViewGroup
import me.proxer.app.GlideRequests

import me.proxer.app.anime.AnimeViewModel
import me.proxer.app.util.extension.safeInject
import me.proxer.library.ProxerApi
import me.proxer.library.entity.list.MediaListEntry
import timber.log.Timber
import java.util.logging.Logger

/**
 * This abstract, generic class will create and manage the
 * ViewHolder and will provide typed Presenter callbacks such that you do not have to perform casts
 * on your own.
 * @author Graphicscore (Dominik Louven)
 * @param <T> View type for the card.
</T> */
abstract class AbstractCardPresenter<T : BaseCardView>
/**
 * @param context The current context.
 */
    (val context: Context, val glide: GlideRequests?) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val cardView = onCreateView()
        return Presenter.ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        @Suppress("UNCHECKED_CAST")
        onBindViewHolder(item, viewHolder.view as T)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        @Suppress("UNCHECKED_CAST")
        onUnbindViewHolder(viewHolder.view as T)
    }

    fun onUnbindViewHolder(cardView: T) {
        Timber.d(String.format("unbinding %s",cardView.id))
        // Nothing to clean up. Override if necessary.
    }

    /**
     * Invoked when a new view is created.
     *
     * @return Returns the newly created view.
     */
    protected abstract fun onCreateView(): T

    /**
     * Implement this method to update your card's view with the data bound to it.
     *
     * @param object The model containing the data for the card.
     * @param cardView The view the card is bound to.
     * @see Presenter.onBindViewHolder
     */
    abstract fun onBindViewHolder(viewHolder: Any, cardView: T)

    companion object {

        private val TAG = "AbstractCardPresenter"
    }

}
