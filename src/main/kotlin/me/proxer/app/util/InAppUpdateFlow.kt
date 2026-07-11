package me.proxer.app.util

import android.app.Activity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import timber.log.Timber

/**
 * @author Ruben Gees
 */
class InAppUpdateFlow {
    companion object {
        const val REQUEST_CODE = 5276
    }

    private var appUpdateManager: AppUpdateManager? = null

    private lateinit var successListener: OnSuccessListener<in AppUpdateInfo>
    private var progressListener: InstallStateUpdatedListener? = null
    private var failureListener: OnFailureListener? = null

    fun start(activity: Activity, onUpdateAvailable: (download: () -> Unit) -> Unit, onUpdateReady: (install: () -> Unit) -> Unit) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
            appUpdateManager =
                AppUpdateManagerFactory.create(activity).also { appUpdateManager ->
                    successListener = successListener(activity, appUpdateManager, onUpdateAvailable)
                    progressListener = progressListener(appUpdateManager, onUpdateReady)
                    failureListener = failureListener()

                    appUpdateManager.appUpdateInfo.addOnSuccessListener(successListener)
                    appUpdateManager.appUpdateInfo.addOnFailureListener(requireNotNull(failureListener))
                    appUpdateManager.registerListener(requireNotNull(progressListener))
                }
        }
    }

    private fun successListener(
        activity: Activity,
        appUpdateManager: AppUpdateManager,
        onUpdateAvailable: (download: () -> Unit) -> Unit,
    ) = OnSuccessListener<AppUpdateInfo> { appUpdateInfo ->
        if (
            appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        ) {
            onUpdateAvailable {
                @Suppress("DEPRECATION")
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE,
                    activity,
                    REQUEST_CODE,
                )
            }
        }
    }

    private fun failureListener() = OnFailureListener { error ->
        Timber.e(error)
    }

    private fun progressListener(appUpdateManager: AppUpdateManager, onUpdateReady: (install: () -> Unit) -> Unit) =
        InstallStateUpdatedListener {
            if (it.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateReady { appUpdateManager.completeUpdate() }
            }
        }

    fun stop() {
        progressListener?.also { appUpdateManager?.unregisterListener(it) }

        appUpdateManager = null
        failureListener = null
        progressListener = null
    }
}
