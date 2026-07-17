package me.proxer.app.base

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.ios.IosEmojiProvider
import me.proxer.app.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Mirrors MainApplication.initLibs(): production code (e.g. DateExtensions.distanceInWordsToNow(),
        // used by NotificationScreen) calls ZoneId.systemDefault(), which throws ZoneRulesException("No
        // time-zone data files registered") unless threetenbp's tzdb has been loaded via this call first.
        AndroidThreeTen.init(this)

        // Also mirrors MainApplication.initLibs(): BetterLinkGifAwareEmojiTextView (built by every BBCodeView
        // rendering text) constructs an EmojiTextView, whose ctor calls EmojiManager.verifyInstalled() and
        // throws unless a provider is installed first -- regardless of whether emoticons are enabled. Any test
        // that renders a comment/post body needs this.
        EmojiManager.install(IosEmojiProvider())

        startKoin {
            androidContext(this@TestApplication)

            modules(listOf(viewModelModule, fakeAppModule()))
        }
    }
}
