package me.proxer.app.tv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import me.proxer.app.auth.LocalUser
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.library.ProxerApi

class TvShellViewModel : ViewModel() {
    private val storageHelper by safeInject<StorageHelper>()
    private val api by safeInject<ProxerApi>()

    val user = MutableLiveData<LocalUser?>(storageHelper.user)
    val logoutSuccess = MutableLiveData<Unit?>()
    val logoutError = MutableLiveData<ErrorUtils.ErrorAction?>()
    val isLoggingOut = MutableLiveData<Boolean?>()

    private val disposables = CompositeDisposable()
    private var logoutDisposable: Disposable? = null

    init {
        disposables +=
            storageHelper.isLoggedInObservable
                .subscribe { user.value = storageHelper.user }
    }

    override fun onCleared() {
        logoutDisposable?.dispose()
        logoutDisposable = null
        disposables.dispose()
        super.onCleared()
    }

    fun logout() {
        if (isLoggingOut.value != true) {
            logoutDisposable?.dispose()
            logoutDisposable =
                api.user
                    .logout()
                    .buildSingle()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe {
                        logoutError.value = null
                        isLoggingOut.value = true
                    }.doAfterTerminate { isLoggingOut.value = false }
                    .subscribeAndLogErrors(
                        { logoutSuccess.value = Unit },
                        { logoutError.value = ErrorUtils.handle(it) },
                    )
        }
    }
}
