package com.eiyooooo.adblink.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class DeviceConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromConnectionEndpointList(endpoints: List<ConnectionEndpoint>?): String? {
        return endpoints?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toConnectionEndpointList(endpointsString: String?): List<ConnectionEndpoint>? {
        return try {
            endpointsString?.let { json.decodeFromString(it) }
        } catch (_: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromConnectionEndpoint(endpoint: ConnectionEndpoint?): String? {
        return endpoint?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toConnectionEndpoint(endpointString: String?): ConnectionEndpoint? {
        return try {
            endpointString?.let { json.decodeFromString(it) }
        } catch (_: Exception) {
            null
        }
    }
}
