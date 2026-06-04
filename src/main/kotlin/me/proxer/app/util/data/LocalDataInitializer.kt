@file:Suppress("DEPRECATION")

package me.proxer.app.util.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * @author Ruben Gees
 */
class LocalDataInitializer(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val storagePreferences: SharedPreferences,
) {
    private companion object {
        private const val VERSION = "version"

        private const val CURRENT_VERSION = 7
    }

    @Volatile
    private var isInitialized = false

    fun initAndMigrateIfNecessary() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    val previousVersion = preferences.getInt(VERSION, 0)

                    if (previousVersion <= 5) {
                        // Hawk (Conceal-based encrypted storage) was used in versions ≤5.
                        // Users upgrading directly from those versions lose non-critical cached
                        // preferences; they will need to re-authenticate.
                        cleanUpHawkFiles()
                    }

                    if (previousVersion <= 6) {
                        migrate6To7(storagePreferences)
                    }

                    if (previousVersion != CURRENT_VERSION) {
                        preferences.edit(commit = true) { putInt(VERSION, CURRENT_VERSION) }
                    }

                    isInitialized = true
                }
            }
        }
    }

    private fun cleanUpHawkFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences("Hawk2")
            context.deleteSharedPreferences("crypto.KEY_256")
        } else {
            arrayOf("shared_prefs/Hawk2.xml", "shared_prefs/crypto.KEY_256.xml")
                .map { File(context.filesDir.parent, it) }
                .filter { it.exists() }
                .forEach { it.delete() }
        }
    }

    private fun migrate6To7(storagePreferences: SharedPreferences) {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        val previousPreferences =
            EncryptedSharedPreferences.create(
                context,
                "me.proxer.encrypted_preferences.xml",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        storagePreferences.edit(commit = true) {
            previousPreferences.all.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences("me.proxer.encrypted_preferences.xml")
        } else {
            File(context.filesDir.parent, "shared_prefs/me.proxer.encrypted_preferences.xml.xml")
                .let { if (it.exists()) it else null }
                ?.delete()
        }
    }
}
