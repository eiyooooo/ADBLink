package com.eiyooooo.adblink.data

import androidx.room.TypeConverter

class DeviceConverters {

    @TypeConverter
    fun fromHostPort(hostPort: HostPort?): String? {
        return hostPort?.let {
            "${it.host}:${it.port}"
        }
    }

    @TypeConverter
    fun toHostPort(hostPortString: String?): HostPort? {
        return try {
            hostPortString?.let {
                val parts = it.split(":")
                if (parts.size == 2) {
                    HostPort(parts[0], parts[1].toInt())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
