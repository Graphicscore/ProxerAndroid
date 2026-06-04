@file:Suppress("NOTHING_TO_INLINE", "MethodOverloading")

package me.proxer.app.util.extension

import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.uber.autodispose.CompletableSubscribeProxy
import com.uber.autodispose.FlowableSubscribeProxy
import com.uber.autodispose.ObservableSubscribeProxy
import com.uber.autodispose.SingleSubscribeProxy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import timber.log.Timber

inline fun Observer<*>.checkMainThread(): Boolean =
    if (Looper.myLooper() != Looper.getMainLooper()) {
        val threadName = Thread.currentThread().name

        onSubscribe(Disposables.empty())
        onError(IllegalStateException("Expected to be called on the main thread but was $threadName"))

        false
    } else {
        true
    }

inline fun <reified I, reified O> Observable<I>.mapBindingAdapterPosition(
    noinline bindingAdapterPosition: (I) -> Int,
    noinline mapper: (Int) -> O,
): Observable<O> =
    this
        .map(bindingAdapterPosition)
        .filter { it != RecyclerView.NO_POSITION }
        .map(mapper)

inline fun <T> Observable<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> Observable<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> Observable<T>.subscribeAndLogErrors(): Disposable? =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> Flowable<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> Flowable<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> Flowable<T>.subscribeAndLogErrors(): Disposable? =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> Single<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> Single<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> Single<T>.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun Completable.subscribeAndLogErrors(
    noinline onSuccess: () -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun Completable.subscribeAndLogErrors(noinline onSuccess: () -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun Completable.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> Maybe<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> Maybe<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> Maybe<T>.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> ObservableSubscribeProxy<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> ObservableSubscribeProxy<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> ObservableSubscribeProxy<T>.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> FlowableSubscribeProxy<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> FlowableSubscribeProxy<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> FlowableSubscribeProxy<T>.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun <T> SingleSubscribeProxy<T>.subscribeAndLogErrors(
    noinline onSuccess: (T) -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun <T> SingleSubscribeProxy<T>.subscribeAndLogErrors(noinline onSuccess: (T) -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun <T> SingleSubscribeProxy<T>.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )

inline fun CompletableSubscribeProxy.subscribeAndLogErrors(
    noinline onSuccess: () -> Unit,
    noinline onError: (Throwable) -> Unit,
): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
        onError(it)
    }

inline fun CompletableSubscribeProxy.subscribeAndLogErrors(noinline onSuccess: () -> Unit): Disposable =
    this.subscribe(onSuccess) {
        Timber.e(it)
    }

inline fun CompletableSubscribeProxy.subscribeAndLogErrors(): Disposable =
    this.subscribe(
        {},
        {
            Timber.e(it)
        },
    )
