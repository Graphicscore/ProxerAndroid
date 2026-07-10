package me.proxer.app.base

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Forces all RxJava/RxAndroid schedulers to [Schedulers.trampoline] for the duration of a test,
 * so `subscribeOn`/`observeOn` chains in ViewModels execute synchronously on the test thread.
 */
class RxTrampolineRule : TestWatcher() {
    override fun starting(description: Description) {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
    }

    override fun finished(description: Description) {
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }
}
