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
}

fun stubLoggedOut(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper, validators: Validators) {
    every { storageHelper.isLoggedInObservable } returns Observable.never()
    every { storageHelper.isLoggedIn } returns false
    every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
    every { validators.validateLogin() } throws NotLoggedInException()
}
