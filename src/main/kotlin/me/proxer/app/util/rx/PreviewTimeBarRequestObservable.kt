package me.proxer.app.util.rx

import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.MainThreadDisposable
import me.proxer.app.util.extension.checkMainThread

class PreviewTimeBarRequestObservable(
    private val view: DefaultTimeBar,
) : Observable<Long>() {
    override fun subscribeActual(observer: Observer<in Long>) {
        if (!observer.checkMainThread()) {
            return
        }

        val listener = Listener(view, observer)

        observer.onSubscribe(listener)

        view.addListener(listener)
    }

    internal class Listener(
        private val view: DefaultTimeBar,
        private val observer: Observer<in Long>,
    ) : MainThreadDisposable(),
        TimeBar.OnScrubListener {
        override fun onScrubStart(
            timeBar: TimeBar,
            position: Long,
        ) {
            emit(position)
        }

        override fun onScrubMove(
            timeBar: TimeBar,
            position: Long,
        ) {
            emit(position)
        }

        override fun onScrubStop(
            timeBar: TimeBar,
            position: Long,
            canceled: Boolean,
        ) = Unit

        private fun emit(position: Long) {
            if (!isDisposed) {
                try {
                    observer.onNext(position)
                } catch (e: Exception) {
                    observer.onError(e)
                    dispose()
                }
            }
        }

        override fun onDispose() {
            view.removeListener(this)
        }
    }
}
