package me.ash.reader.domain.model.account

import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import me.ash.reader.infrastructure.preference.AutoMarkAsReadPreference

/**
 * Provide [TypeConverter] of [AutoMarkAsReadPreference] for [RoomDatabase].
 */
class AutoMarkAsReadConverters {

    @TypeConverter
    fun toAutoMarkAsRead(autoMarkAsRead: Long): AutoMarkAsReadPreference {
        return AutoMarkAsReadPreference.values.find { it.value == autoMarkAsRead }
            ?: AutoMarkAsReadPreference.default
    }

    @TypeConverter
    fun fromAutoMarkAsRead(autoMarkAsRead: AutoMarkAsReadPreference): Long {
        return autoMarkAsRead.value
    }
}
