package com.eiyooooo.adblink.util

fun generateRandomString(length: Int): String {
    @Suppress("SpellCheckingInspection")
    val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-+*/<>{}"
    return (1..length).map { charSet.random() }.joinToString("")
}
