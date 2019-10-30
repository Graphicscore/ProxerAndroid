package me.proxer.app.news.widget

import android.os.Parcel
import android.os.Parcelable
import com.squareup.moshi.JsonClass
import me.proxer.app.util.extension.readStringSafely
import org.threeten.bp.Instant

/**
 * @author Ruben Gees
 */
@JsonClass(generateAdapter = true)
data class SimpleNews(
    val id: String,
    val threadId: String,
    val categoryId: String,
    val subject: String,
    val category: String,
    val date: Instant
) : Parcelable {

    companion object {
        @Suppress("unused")
        @JvmField
        val CREATOR = object : Parcelable.Creator<SimpleNews> {
            override fun createFromParcel(parcel: Parcel) = SimpleNews(parcel)
            override fun newArray(size: Int): Array<SimpleNews?> = arrayOfNulls(size)
        }
    }

    constructor(parcel: Parcel) : this(
        parcel.readStringSafely(),
        parcel.readStringSafely(),
        parcel.readStringSafely(),
        parcel.readStringSafely(),
        parcel.readStringSafely(),
        Instant.ofEpochMilli(parcel.readLong())
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(threadId)
        parcel.writeString(categoryId)
        parcel.writeString(subject)
        parcel.writeString(category)
        parcel.writeLong(date.toEpochMilli())
    }

    override fun describeContents() = 0
}
