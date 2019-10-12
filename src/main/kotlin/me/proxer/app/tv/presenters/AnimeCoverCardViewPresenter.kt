package me.proxer.app.tv.presenters

/**
 * @author Graphicscore (Dominik Louven)
 */

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.core.view.ViewCompat

import androidx.leanback.widget.ImageCardView
import androidx.lifecycle.ViewModel

import com.bumptech.glide.Glide
import me.proxer.app.GlideRequests
import me.proxer.app.util.extension.safeInject
import me.proxer.app.R
import me.proxer.app.util.extension.defaultLoad
import me.proxer.library.ProxerApi
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.util.ProxerUrls

/**
 * A very basic [ImageCardView] [androidx.leanback.widget.Presenter].You can
 * pass a custom style for the ImageCardView in the constructor. Use the default constructor to
 * create a Presenter with a default ImageCardView style.
 */
class AnimeCoverCardViewPresenter @JvmOverloads constructor(
    context: Context,
    glide: GlideRequests?,
    cardThemeResId: Int = R.style.DefaultCardTheme
) : AbstractCardPresenter<ImageCardView>(ContextThemeWrapper(context, cardThemeResId), glide) {

    override fun onCreateView(): ImageCardView {
//        imageCardView.setOnClickListener(new View.OnClickListener() {
        //            @Override
        //            public void onClick(View v) {
        //                Toast.makeText(getContext(), "Clicked on ImageCardView", Toast.LENGTH_SHORT).show();
        //            }
        //        });
        return ImageCardView(context)
    }

    override fun onBindViewHolder(viewHolder: Any, cardView: ImageCardView) {
        val anime = viewHolder as MediaListEntry
        cardView.titleText = anime.name

        ViewCompat.setTransitionName(cardView.mainImageView, "media_${anime.id}")
        glide?.defaultLoad(cardView.mainImageView, ProxerUrls.entryImage(anime.id))
        /*cardView.setTag(anime);
        cardView.setTitleText(anime.getSimpleInformation().getName());
        cardView.setContentText(anime.getSimpleInformation().getShortDescription());
        if (anime.getSimpleInformation().getCoverURL() != null) {
            Glide.with(getContext())
                    .load(anime.getSimpleInformation().getCoverURL())
                    .asBitmap()
                    .into(cardView.getMainImageView());
        }*/
        Log.d("TV", "onBindViewHolder in AnimeCoverCardViewPresenter")
    }

}
