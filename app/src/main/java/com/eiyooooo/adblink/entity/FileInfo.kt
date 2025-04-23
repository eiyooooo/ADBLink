package com.eiyooooo.adblink.entity

data class FileInfo(
    val mode: Int,
    val size: Long,
    val lastModified: Long,
    val name: String
) {
    val isDirectory: Boolean
        get() = (mode and 0x4000) != 0
}
