package me.proxer.app.tv.activity

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import me.proxer.app.R
import me.proxer.app.anime.AnimeViewModel
import me.proxer.app.media.MediaInfoViewModel
import me.proxer.app.media.episode.EpisodeViewModel
import me.proxer.app.tv.fragments.DetailViewFragment
import me.proxer.app.tv.fragments.LoadingFragment
import me.proxer.library.entity.info.Entry
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

/**
 * @author Graphicscore (Dominik Louven)
 */
class DetailActivity : FragmentActivity(){
    companion object {
        val ID_EXTRA = "ID"
    }

    val id: String
    get() {
        return intent.extras?.getString(ID_EXTRA)!!
    }

    val viewModel by viewModel<MediaInfoViewModel> { parametersOf(id) }
    val episodeViewModel by viewModel<EpisodeViewModel> { parametersOf(id) }

    lateinit var detailFragment: DetailViewFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_detail)

        if (savedInstanceState == null) {
            val fragment = LoadingFragment()
            supportFragmentManager.beginTransaction()
                //.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out, android.R.anim.fade_in,android.R.anim.fade_out)
                .replace(R.id.details_fragment, fragment)
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.error.observe(this, Observer {
            when (it) {
                null -> {
                    //hideError()
                    Timber.e("Unknown Error?")
                }
                else -> {
                    //showError(it)
                    Timber.e(String.format("Error : %s",it.message))
                    Toast.makeText(this,String.format("Error : %s",it.message),Toast.LENGTH_SHORT).show()
                    onDestroy()
                }
            }
        })

        viewModel.data.observe(this, Observer {
            when (it) {
                null -> hideData()
                else -> {
                    showData(it)
                    loadEpisodes()
                }
            }
        })

        if (viewModel.isLoading.value != true && viewModel.data.value == null && viewModel.error.value == null) {
            viewModel.load()
        }
    }

    private fun hideData(){
        Timber.d("hideData")
    }

    private fun loadEpisodes(){
        episodeViewModel.error.observe(this, Observer {
            when (it){
                null -> {
                    Timber.e("Unknown Error?")
                }
                else -> {
                    Timber.e(String.format("Error Loading episodes: %s",it.message))
                    episodeViewModel.reload()
                }
            }
        })

        episodeViewModel.data.observe(this, Observer {
            detailFragment.onEpisodeListLoaded(it)
        })

        if (episodeViewModel.isLoading.value != true && episodeViewModel.data.value == null && episodeViewModel.error.value == null) {
            episodeViewModel.load()
        }
    }

    private fun showData(entry: Entry){
        detailFragment = DetailViewFragment.newInstance(entry)
        supportFragmentManager.beginTransaction()
            //.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out, android.R.anim.fade_in,android.R.anim.fade_out)
            .replace(R.id.details_fragment,detailFragment)
            .commit()
    }
}
