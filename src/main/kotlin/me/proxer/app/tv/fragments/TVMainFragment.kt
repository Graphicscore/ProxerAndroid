package me.proxer.app.tv.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import io.reactivex.Observable
import me.proxer.app.R
import java.lang.IllegalArgumentException

/**
 * @author Graphicscore (Dominik Louven)
 */
class TVMainFragment : BrowseSupportFragment() {

    companion object {

        enum class HeaderItem(val id: Long) {
            ANIME(100L),
            LOGIN(101L),
            LOGOUT(102L),
            USER(103L),
            UCP(104L),
            SETTINGS(105L);
        }

    }

    private lateinit var backgroundManager: BackgroundManager

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        //load data
        //loadData();

        prepareBackgroundManager()
        setupUIElements()

        mainFragmentRegistry.registerFragment(PageRow::class.java, PageRowFragmentFactory())

    }

    fun prepareBackgroundManager(){
        backgroundManager = BackgroundManager.getInstance(activity)
        backgroundManager.attach(activity?.window)
    }

    fun setupUIElements(){
        adapter = ArrayObjectAdapter(ListRowPresenter())
        (adapter as ArrayObjectAdapter).add(
            PageRow(
                HeaderItem(
                    HeaderItem.ANIME.id,"Anime")
            )
        )
    }

    class PageRowFragmentFactory : BrowseSupportFragment.FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any?): Fragment {
            var row : Row = rowObj as Row
            when(row.headerItem.id){
                HeaderItem.ANIME.id -> return AllAnimeFragment()
            }
             return AllAnimeFragment()
        }

    }
}
