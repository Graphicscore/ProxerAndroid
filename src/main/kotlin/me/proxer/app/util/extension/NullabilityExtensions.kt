@file:Suppress("NOTHING_TO_INLINE")

package me.proxer.app.util.extension

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.widget.EditText
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.recyclerview.widget.RecyclerView

inline val RecyclerView.safeLayoutManager: RecyclerView.LayoutManager
    get() = requireNotNull(layoutManager)

inline val EditText.safeText: Editable
    get() = requireNotNull(text)

inline fun Intent.getSafeStringExtra(key: String) =
    requireNotNull(getStringExtra(key)) { "No value found for key $key" }

inline fun Intent.getSafeStringArrayExtra(key: String): Array<out String> =
    requireNotNull(getStringArrayExtra(key)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Intent.getSafeParcelableExtra(key: String) =
    requireNotNull(IntentCompat.getParcelableExtra(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Bundle.getSafeParcelable(key: String) =
    requireNotNull(BundleCompat.getParcelable(this, key, T::class.java)) { "No value found for key $key" }

inline fun <reified T : Parcelable> Bundle.getSafeParcelableArrayList(key: String) =
    requireNotNull(BundleCompat.getParcelableArrayList(this, key, T::class.java)) { "No value found for key $key" }

inline fun Bundle.getSafeCharSequence(key: String) =
    requireNotNull(getCharSequence(key)) { "No value found for key $key" }

inline fun Bundle.getSafeString(key: String) = requireNotNull(getString(key)) { "No value found for key $key" }

inline fun Parcel.readStringSafely() = requireNotNull(readString()) { "No value available at this position" }

inline fun SharedPreferences.getSafeString(
    key: String,
    default: String? = null,
) = requireNotNull(getString(key, default)) { "No value found for key $key" }
