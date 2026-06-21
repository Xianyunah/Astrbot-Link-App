package com.rainnya.chat.data.local

import androidx.room.TypeConverter
import com.rainnya.chat.data.model.MessageRole

class Converters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = MessageRole.valueOf(value)
}
