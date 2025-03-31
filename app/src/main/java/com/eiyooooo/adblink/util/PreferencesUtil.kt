package com.eiyooooo.adblink.util

import android.content.SharedPreferences
import androidx.core.content.edit

@Suppress("UNCHECKED_CAST")
inline fun <reified T> SharedPreferences.put(key: String, value: T?) {
    edit {
        when (value) {
            null -> putString(key, null)

            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)

            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> SharedPreferences.get(key: String, defaultValue: T): T =
    when (defaultValue) {
        is Boolean -> getBoolean(key, defaultValue) as T
        is Int -> getInt(key, defaultValue) as T
        is Long -> getLong(key, defaultValue) as T
        is Float -> getFloat(key, defaultValue) as T

        is String -> getString(key, defaultValue) as T
        is Set<*> -> getStringSet(key, defaultValue as Set<String>) as T

        else -> error("Type of $defaultValue is not supported")
    }

@Suppress("UNCHECKED_CAST")
inline fun <reified T> SharedPreferences.getNullable(key: String, defaultValue: T?): T? =
    when (defaultValue) {
        null -> getString(key, null) as T?

        is String -> getString(key, defaultValue) as T?
        is Set<*> -> getStringSet(key, defaultValue as Set<String>) as T?

        else -> error("Type of $defaultValue is not supported")
    }
