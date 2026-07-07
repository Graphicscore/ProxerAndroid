package me.proxer.app.util.logging

import android.util.Log
import androidx.work.Logger
import timber.log.Timber

/**
 * @author Ruben Gees
 */
class WorkManagerTimberLogger(loggingLevel: Int = Log.INFO) : Logger(loggingLevel) {
    override fun verbose(tag: String, message: String) {
        Timber.tag(tag).v(message)
    }

    override fun verbose(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).v(throwable, message)
    }

    override fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    override fun debug(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).d(throwable, message)
    }

    override fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    override fun info(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).i(throwable, message)
    }

    override fun warning(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    override fun warning(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).w(throwable, message)
    }

    override fun error(tag: String, message: String) {
        Timber.tag(tag).e(message)
    }

    override fun error(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).e(throwable, message)
    }
}
