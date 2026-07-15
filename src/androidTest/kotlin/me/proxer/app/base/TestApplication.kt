package me.proxer.app.base

import android.app.Application
import me.proxer.app.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TestApplication)

            modules(listOf(viewModelModule, fakeAppModule()))
        }
    }
}
