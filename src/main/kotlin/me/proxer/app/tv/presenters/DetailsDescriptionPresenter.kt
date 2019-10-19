package me.proxer.app.tv.presenters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import me.proxer.app.R
import me.proxer.library.entity.info.Entry

/**
 * @author Graphicscore (Dominik Louven)
 */
class DetailsDescriptionPresenter constructor(private val mContext: Context) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.tv_fragment_detail_content, null)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val entry : Entry = item as Entry
        val primaryText = viewHolder.view.findViewById<TextView>(R.id.primary_text)
        val sndText1 = viewHolder.view.findViewById<TextView>(R.id.secondary_text_first)
        val sndText2 = viewHolder.view.findViewById<TextView>(R.id.secondary_text_second)
        val extraText = viewHolder.view.findViewById<TextView>(R.id.extra_text)

        primaryText.text = entry.name
        sndText1.text = entry.seasons.first().year.toString().let {
            it + ""
        }
        sndText2.text = entry.genres.joinToString {
            it.name
        }
        extraText.text = entry.description
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // Nothing to do here.
    }
}
