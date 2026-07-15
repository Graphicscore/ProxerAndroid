package me.proxer.app.base

import io.mockk.every
import io.reactivex.Observable
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper

fun stubLoggedIn(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper) {
    every { storageHelper.isLoggedInObservable } returns Observable.never()
    every { storageHelper.isLoggedIn } returns true
    every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
    // PreferenceHelper.themeObservable is a real io.reactivex.Observable<ThemeContainer>, not an interface.
    // Left unstubbed on the relaxed PreferenceHelper mock, MockK's relaxed-mode auto value generation on
    // Android hands BaseActivity.onCreate()'s `.autoDisposable(scope())` chain an object that isn't a real
    // Observable, crashing every BaseActivity-based screen on launch.
    every { preferenceHelper.themeObservable } returns Observable.never()
}

fun stubLoggedOut(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper, validators: Validators) {
    every { storageHelper.isLoggedInObservable } returns Observable.never()
    every { storageHelper.isLoggedIn } returns false
    every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
    every { preferenceHelper.themeObservable } returns Observable.never()
    every { validators.validateLogin() } throws NotLoggedInException()
}
