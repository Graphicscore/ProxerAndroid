package com.proxerme.app.application

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.config.Configuration
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import com.orhanobut.hawk.Hawk
import com.orhanobut.hawk.HawkBuilder
import com.proxerme.app.BuildConfig
import com.proxerme.library.connection.ProxerConnection
import com.proxerme.library.info.ProxerUrlHolder
import com.proxerme.library.util.PersistentCookieStore
import net.danlew.android.joda.JodaTimeAndroid
import org.greenrobot.eventbus.EventBus
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * TODO: Describe Class
 *
 * @author Ruben Gees
 */
class MainApplication : Application() {

    companion object {
        lateinit var jobManager: JobManager
    }

    override fun onCreate() {
        super.onCreate()

        JodaTimeAndroid.init(this)
        Hawk.init(this)
                .setEncryptionMethod(HawkBuilder.EncryptionMethod.MEDIUM)
                .setStorage(HawkBuilder.newSharedPrefStorage(this))
                .build()
        EventBus.builder()
                .logNoSubscriberMessages(false)
                .sendNoSubscriberEvent(false)
                .installDefaultEventBus()
        DrawerImageLoader.init(object : DrawerImageLoader.IDrawerImageLoader {
            override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable?) {
                Glide.with(imageView.context)
                        .load(uri)
                        .placeholder(IconicsDrawable(imageView.context,
                                CommunityMaterial.Icon.cmd_account)
                                .colorRes(android.R.color.white))
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .centerCrop()
                        .into(imageView)
            }

            override fun cancel(imageView: ImageView) {
                Glide.clear(imageView)
            }

            override fun placeholder(ctx: Context): Drawable? {
                return null
            }

            override fun placeholder(ctx: Context, tag: String): Drawable? {
                return null
            }
        })

        jobManager = JobManager(Configuration.Builder(this).build())

        ProxerConnection.init(BuildConfig.PROXER_API_KEY)
        CookieHandler.setDefault(CookieManager(PersistentCookieStore(this),
                CookiePolicy { uri, httpCookie ->
                    uri.scheme + "://" + uri.host == ProxerUrlHolder.getHost()
                }))
    }
}