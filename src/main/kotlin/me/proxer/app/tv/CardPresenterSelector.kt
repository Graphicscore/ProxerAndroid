package me.proxer.app.tv

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import me.proxer.app.GlideApp
import me.proxer.app.GlideRequests
import me.proxer.app.tv.presenters.AnimeCoverCardViewPresenter

/**
 * @author Graphicscore (Dominik Louven)
 */
class CardPresenterSelector : PresenterSelector {

    private var context : Context
    private var glide: GlideRequests? = null
    private lateinit var  imageCardViewPresenter : AnimeCoverCardViewPresenter

    constructor(context: Context){
        this.context = context
        this.glide = GlideApp.with(context)
    }

    //currently only support imagecardviews
    override fun getPresenter(item: Any?): Presenter {
        //if(imageCardViewPresenter == null){
            imageCardViewPresenter = AnimeCoverCardViewPresenter(context, glide)
        //}
        return imageCardViewPresenter
    }
}
