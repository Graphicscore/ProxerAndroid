package me.proxer.app.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import me.proxer.app.R

/**
 * @author Graphicscore (Dominik Louven)
 */
class TVMainActivity() : FragmentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)
    }
}
