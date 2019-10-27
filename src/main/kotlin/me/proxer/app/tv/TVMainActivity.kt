package me.proxer.app.tv

import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import me.proxer.app.R
import me.proxer.app.tv.activity.AuthenticationActivity
import me.proxer.app.tv.activity.WebbrowserActivity
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.androidUri
import me.proxer.app.util.extension.openHttpPage
import me.proxer.app.util.extension.safeInject
import me.zhanghai.android.customtabshelper.CustomTabsHelperFragment
import okhttp3.HttpUrl
import kotlin.properties.Delegates

/**
 * @author Graphicscore (Dominik Louven)
 */
class TVMainActivity() : FragmentActivity()
{

    fun showPage(url: HttpUrl){
        startActivity(Intent(this,WebbrowserActivity::class.java).apply {
            putExtra(WebbrowserActivity.EXTRA_URL,url.androidUri())
        })
    }


    private val storageHelper by safeInject<StorageHelper>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_main)

        //if not logged in launch login
        if(storageHelper.user == null) {
            startActivity(Intent(this, AuthenticationActivity::class.java))
            finish()
        }
    }
}
