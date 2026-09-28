package com.analoganchor.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toList(data: String): List<String> {
        return if (data.isEmpty()) emptyList() else data.split(",")
    }

    @TypeConverter
    fun fromFocusType(type: com.analoganchor.app.data.local.entity.FocusType): String {
        return type.name
    }

    @TypeConverter
    fun toFocusType(value: String): com.analoganchor.app.data.local.entity.FocusType {
        return try {
            com.analoganchor.app.data.local.entity.FocusType.valueOf(value)
        } catch (e: Exception) {
            com.analoganchor.app.data.local.entity.FocusType.SHIELD
        }
    }
}
