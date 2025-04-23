package com.eiyooooo.adblink.entity

data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedMBps: Double,
    val progress: Int
)
