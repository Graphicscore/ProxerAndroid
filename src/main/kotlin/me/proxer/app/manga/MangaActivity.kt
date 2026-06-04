package me.proxer.app.manga

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.content.IntentCompat
import androidx.core.os.postDelayed
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commitNow
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.jakewharton.rxbinding3.view.clicks
import com.mikepenz.iconics.utils.IconicsMenuInflaterUtil
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import me.proxer.app.R
import me.proxer.app.base.BaseActivity
import me.proxer.app.media.MediaActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
import me.proxer.library.util.ProxerUrls
import me.proxer.library.util.ProxerUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Ruben Gees
 */
class MangaActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val EPISODE_EXTRA = "episode"
        private const val LANGUAGE_EXTRA = "language"
        private const val CHAPTER_TITLE_EXTRA = "chapter_title"
        private const val NAME_EXTRA = "name"
        private const val EPISODE_AMOUNT_EXTRA = "episode_amount"

        fun navigateTo(
            context: Activity,
            id: String,
            episode: Int,
            language: Language,
            chapterTitle: String?,
            name: String? = null,
            episodeAmount: Int? = null,
        ) {
            context.startActivity<MangaActivity>(
                ID_EXTRA to id,
                EPISODE_EXTRA to episode,
                LANGUAGE_EXTRA to language,
                CHAPTER_TITLE_EXTRA to chapterTitle,
                NAME_EXTRA to name,
                EPISODE_AMOUNT_EXTRA to episodeAmount,
            )
        }
    }

    val id: String
        get() =
            when (intent.hasExtra(ID_EXTRA)) {
                true -> intent.getSafeStringExtra(ID_EXTRA)
                false -> intent.data?.pathSegments?.getOrNull(1) ?: "-1"
            }

    var episode: Int
        get() =
            when (intent.hasExtra(EPISODE_EXTRA)) {
                true -> {
                    intent.getIntExtra(EPISODE_EXTRA, 1)
                }

                false -> {
                    intent.data
                        ?.pathSegments
                        ?.getOrNull(2)
                        ?.toIntOrNull() ?: 1
                }
            }
        set(value) {
            intent.putExtra(EPISODE_EXTRA, value)

            updateTitle()
        }

    val language: Language
        get() =
            when (intent.hasExtra(LANGUAGE_EXTRA)) {
                true -> {
                    requireNotNull(IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, Language::class.java)) {
                        "LANGUAGE_EXTRA present but deserialized to null in MangaActivity"
                    }
                }

                false -> {
                    intent.data
                        ?.pathSegments
                        ?.getOrNull(3)
                        ?.let { ProxerUtils.toApiEnum<Language>(it) }
                        ?: Language.ENGLISH
                }
            }

    var chapterTitle: String?
        get() = intent.getStringExtra(CHAPTER_TITLE_EXTRA)
        set(value) {
            intent.putExtra(CHAPTER_TITLE_EXTRA, value)

            updateTitle()
        }

    var name: String?
        get() = intent.getStringExtra(NAME_EXTRA)
        set(value) {
            intent.putExtra(NAME_EXTRA, value)

            updateTitle()
        }

    var episodeAmount: Int?
        get() =
            when (intent.hasExtra(EPISODE_AMOUNT_EXTRA)) {
                true -> intent.getIntExtra(EPISODE_AMOUNT_EXTRA, 1)
                else -> null
            }
        set(value) {
            when (value) {
                null -> intent.removeExtra(EPISODE_AMOUNT_EXTRA)
                else -> intent.putExtra(EPISODE_AMOUNT_EXTRA, value)
            }
        }

    private val viewModel by viewModel<MangaViewModel> { parametersOf(id, language, episode) }

    private val toolbar: Toolbar by bindView(R.id.toolbar)

    private val fullscreenHandler = Handler(Looper.getMainLooper())

    private var isInFullscreenMode = false
    private val systemBarsVisibilitySubject = PublishSubject.create<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_manga)
        setSupportActionBar(toolbar)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val systemBarsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
            systemBarsVisibilitySubject.onNext(systemBarsVisible)
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        setupToolbar()
        updateTitle()

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                replace(R.id.container, MangaFragment.newInstance())
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (viewModel.data.value != null) {
            onContentShow()
        } else {
            onContentHide()
        }
    }

    override fun onDestroy() {
        fullscreenHandler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        IconicsMenuInflaterUtil.inflate(menuInflater, this, R.menu.activity_share, menu, true)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                name?.let {
                    val link = ProxerUrls.mangaWeb(id, episode, language)

                    val text =
                        chapterTitle.let { title ->
                            when {
                                title.isNullOrBlank() -> getString(R.string.share_manga, episode, it, link)
                                else -> getString(R.string.share_manga_title, title, it, link)
                            }
                        }

                    ShareCompat
                        .IntentBuilder(this)
                        .setText(text)
                        .setType("text/plain")
                        .setChooserTitle(getString(R.string.share_title))
                        .startChooser()
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)

        if (!isInMultiWindowMode) {
            if (viewModel.data.value != null) {
                setFullscreen(true)
            } else {
                setFullscreen(false)
            }
        }
    }

    fun onContentShow() {
        fullscreenHandler.removeCallbacksAndMessages(null)

        setFullscreen(true)

        systemBarsVisibilitySubject
            .autoDisposable(this.scope())
            .subscribe { systemBarsVisible ->
                if (systemBarsVisible) {
                    setFullscreen(false)

                    fullscreenHandler.postDelayed(3000) {
                        onContentShow()
                    }
                } else {
                    setFullscreen(true)
                }
            }
    }

    fun onContentHide() {
        fullscreenHandler.removeCallbacksAndMessages(null)

        setFullscreen(false)
    }

    fun toggleFullscreen() {
        fullscreenHandler.removeCallbacksAndMessages(null)

        setFullscreen(!isInFullscreenMode)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        val isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInMultiWindowMode
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (fullscreen && !isInMultiWindowMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            isInFullscreenMode = true

            toolbar.isVisible = false
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            isInFullscreenMode = false

            toolbar.isVisible = true
        }

        if (isInMultiWindowMode) {
            toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS
            }
        } else {
            toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = SCROLL_FLAG_NO_SCROLL
            }
        }
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar
            .clicks()
            .autoDisposable(this.scope())
            .subscribe {
                name?.let {
                    MediaActivity.navigateTo(this, id, name, Category.MANGA)
                }
            }
    }

    private fun updateTitle() {
        title = name
        supportActionBar?.subtitle = chapterTitle ?: Category.MANGA.toEpisodeAppString(this, episode)
    }
}
