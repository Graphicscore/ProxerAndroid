package me.proxer.app.util.converter

import androidx.room.TypeConverter
import me.proxer.library.enums.Device
import me.proxer.library.enums.MessageAction
import me.proxer.library.enums.TagSubType
import me.proxer.library.enums.TagType
import me.proxer.library.util.ProxerUtils
import org.threeten.bp.Instant
import java.util.Date

/**
 * @author Ruben Gees
 */
@Suppress("unused")
class RoomConverters {

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun fromInstant(date: Instant?): Long? = date?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromMessageAction(value: MessageAction?): String? = value?.let { ProxerUtils.getSafeApiEnumName(it) }

    @TypeConverter
    fun toMessageAction(value: String?): MessageAction? = value?.let { ProxerUtils.toSafeApiEnum<MessageAction>(it) }

    @TypeConverter
    fun fromDevice(value: Device?): String? = value?.let { ProxerUtils.getSafeApiEnumName(it) }

    @TypeConverter
    fun toDevice(value: String?): Device? = value?.let { ProxerUtils.toSafeApiEnum<Device>(it) }

    @TypeConverter
    fun fromTagType(value: TagType?): String? = value?.let { ProxerUtils.getSafeApiEnumName(it) }

    @TypeConverter
    fun toTagType(value: String?): TagType? = value?.let { ProxerUtils.toSafeApiEnum<TagType>(it) }

    @TypeConverter
    fun fromTagSubType(value: TagSubType?): String? = value?.let { ProxerUtils.getSafeApiEnumName(it) }

    @TypeConverter
    fun toTagSubType(value: String?): TagSubType? = value?.let { ProxerUtils.toSafeApiEnum<TagSubType>(it) }
}
