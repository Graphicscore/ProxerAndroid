package me.proxer.app

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.rubengees.introduction.IntroductionBuilder
import com.rubengees.introduction.IntroductionActivity.OPTION_RESULT
import com.rubengees.introduction.Option
import me.proxer.app.base.BaseActivity
import me.proxer.app.notification.NotificationWorker
import me.proxer.app.settings.theme.Theme
import me.proxer.app.settings.theme.ThemeContainer
import me.proxer.app.settings.theme.ThemeVariant
import me.proxer.app.ui.compose.MainScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.wrapper.IntroductionWrapper
import me.proxer.app.util.wrapper.DrawerItem

/**
 * @author Ruben Gees
 */
class MainActivity : BaseActivity() {
    companion object {
        private const val SECTION_EXTRA = "section"
        private const val SECTION_ACTION_PREFIX = "me.proxer.app.intent.action."

        fun navigateToSection(context: Context, section: DrawerItem) =
            context.startActivity(getSectionIntent(context, section))

        fun getSectionIntent(context: Context, section: DrawerItem): Intent =
            context.intentFor<MainActivity>(SECTION_EXTRA to section)
                .setAction(SECTION_ACTION_PREFIX + section.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (BuildConfig.LOG && VERSION.SDK_INT >= VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)
        }

        val shouldIntroduce = preferenceHelper.launches <= 0 && intent.action == Intent.ACTION_MAIN
        if (shouldIntroduce) {
            preferenceHelper.incrementLaunches()
            IntroductionWrapper.introduce(this)
            return
        }

        if (intent.action == Intent.ACTION_MAIN) {
            preferenceHelper.incrementLaunches()
        }

        val initialItem = getItemToLoad()

        setContent {
            ProxerTheme {
                MainScreen(initialItem = initialItem)
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IntroductionBuilder.INTRODUCTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val options = IntentCompat.getParcelableArrayListExtra(
                    data ?: return,
                    OPTION_RESULT,
                    Option::class.java,
                )

                options?.forEach { option ->
                    when (option.position) {
                        1 -> {
                            preferenceHelper.areNewsNotificationsEnabled = option.isActivated
                            preferenceHelper.areAccountNotificationsEnabled = option.isActivated
                            NotificationWorker.enqueueIfPossible(delay = true)
                        }

                        2 -> {
                            if (option.isActivated) {
                                preferenceHelper.themeContainer = ThemeContainer(Theme.CLASSIC, ThemeVariant.DARK)
                            }
                        }
                    }
                }
            }

            val initialItem = getItemToLoad()
            setContent {
                ProxerTheme {
                    MainScreen(initialItem = initialItem)
                }
            }
        }
    }

    private fun getItemToLoad(): DrawerItem {
        if (intent.action == Intent.ACTION_VIEW) {
            val section = when (intent.data?.pathSegments?.firstOrNull()) {
                "news" -> DrawerItem.NEWS
                "chat" -> DrawerItem.CHAT
                "messages" -> DrawerItem.MESSENGER
                "reminder" -> DrawerItem.BOOKMARKS
                "anime" -> DrawerItem.ANIME
                "calendar" -> DrawerItem.SCHEDULE
                "manga" -> DrawerItem.MANGA
                else -> null
            }
            if (section != null) return section
        }

        val sectionExtra = IntentCompat.getSerializableExtra(intent, SECTION_EXTRA, DrawerItem::class.java)
        return sectionExtra ?: preferenceHelper.startPage
    }
}
