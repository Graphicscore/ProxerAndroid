package me.proxer.app.tv.presenters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import me.proxer.app.R
import me.proxer.app.media.MediaInfoViewModel
import javax.inject.Inject

/**
 * @author Graphicscore (Dominik Louven)
 */
class DetailsDescriptionPresenter constructor(private val mContext: Context) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.tv_detail_view_content, null)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        /*val primaryText = viewHolder.view.findViewById<TextView>(R.id.primary_text)
        val sndText1 = viewHolder.view.findViewById<TextView>(R.id.secondary_text_first)
        val sndText2 =viewHolder.view.findViewById<TextView>(R.id.secondary_text_second)
        val extraText = viewHolder.view.findViewById<TextView>(R.id.extra_text)

        val model = item as MediaInfoViewModel
        primaryText.text = model.data.value?.name
        sndText1.text = model.data.value?.description
        sndText2.text = model.data.value?.seasons?.first()?.year.toString().let {
            it + ""
        }
        extraText.text = model.data.value?.description*/
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // Nothing to do here.
    }
}
