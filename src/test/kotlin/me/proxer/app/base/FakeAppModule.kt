package me.proxer.app.base

import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.media.TagDao
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import okhttp3.OkHttpClient
import org.koin.dsl.module

fun fakeAppModule() = module {
    single<StorageHelper> { mockk(relaxed = true) }
    single<PreferenceHelper> { mockk(relaxed = true) }
    single<ProxerApi> { mockk(relaxed = true) }
    single<RxBus> { RxBus() }
    single<Validators> { mockk(relaxed = true) }
    single<MessengerDao> { mockk(relaxed = true) }
    single<OkHttpClient> { mockk(relaxed = true) }
    single<TagDao> { mockk(relaxed = true) }
}
