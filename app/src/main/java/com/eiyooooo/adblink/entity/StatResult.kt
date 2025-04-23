package com.eiyooooo.adblink.entity

data class StatResult(
    val error: Int = 0,
    val dev: Long = 0,
    val ino: Long = 0,
    val mode: Int = 0,
    val nlink: Int = 0,
    val uid: Int = 0,
    val gid: Int = 0,
    val size: Long = 0,
    val atime: Long = 0,
    val mtime: Long = 0,
    val ctime: Long = 0
) {
    val isDirectory: Boolean
        get() = (mode and 0x4000) != 0

    val isFile: Boolean
        get() = (mode and 0x8000) != 0

    val isSymlink: Boolean
        get() = (mode and 0xA000) != 0
}
