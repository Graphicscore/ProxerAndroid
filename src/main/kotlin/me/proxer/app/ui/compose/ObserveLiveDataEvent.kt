package me.proxer.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Observes a one-shot LiveData event (e.g. a ResettingMutableLiveData error/success signal)
 * via a raw Observer, bypassing Compose's observeAsState()/mutableStateOf structural-equality
 * state diffing. observeAsState() would silently drop the second of two structurally-equal
 * events (Unit == Unit is always true; two identical ErrorActions from repeated identical
 * failures) because Compose skips recomposition when a "new" state value equals the current
 * one - a raw Observer fires once per genuine LiveData delivery regardless.
 */
@Composable
fun <T> ObserveLiveDataEvent(liveData: LiveData<T>, onEvent: (T & Any) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, liveData) {
        val observer = Observer<T> { value -> if (value != null) onEvent(value) }
        liveData.observe(lifecycleOwner, observer)
        onDispose { liveData.removeObserver(observer) }
    }
}
