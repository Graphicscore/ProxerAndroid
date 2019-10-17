package me.proxer.app.tv.activity

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import me.proxer.app.R
import me.proxer.app.tv.fragments.DetailViewFragment

/**
 * @author Graphicscore (Dominik Louven)
 */
class DetailActivity : FragmentActivity(){
    companion object {
        val ID_EXTRA = "ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_detail)

        if (savedInstanceState == null) {
            val fragment = DetailViewFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_fragment, fragment)
                .commit()
        }
    }
}
