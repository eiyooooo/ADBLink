package com.eiyooooo.adblink.model.file

data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedMBps: Double,
    val progress: Int
)