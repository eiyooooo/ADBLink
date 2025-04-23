package com.eiyooooo.adblink.adb

import com.eiyooooo.adblink.adb.LocalServices.ID_DATA
import com.eiyooooo.adblink.adb.LocalServices.ID_DENT_V1
import com.eiyooooo.adblink.adb.LocalServices.ID_DENT_V2
import com.eiyooooo.adblink.adb.LocalServices.ID_DONE
import com.eiyooooo.adblink.adb.LocalServices.ID_FAIL
import com.eiyooooo.adblink.adb.LocalServices.ID_LIST_V1
import com.eiyooooo.adblink.adb.LocalServices.ID_LIST_V2
import com.eiyooooo.adblink.adb.LocalServices.ID_LSTAT_V1
import com.eiyooooo.adblink.adb.LocalServices.ID_LSTAT_V2
import com.eiyooooo.adblink.adb.LocalServices.ID_OKAY
import com.eiyooooo.adblink.adb.LocalServices.ID_QUIT
import com.eiyooooo.adblink.adb.LocalServices.ID_RECV_V1
import com.eiyooooo.adblink.adb.LocalServices.ID_SEND_V1
import com.eiyooooo.adblink.adb.LocalServices.ID_STAT_V2
import com.eiyooooo.adblink.adb.LocalServices.SYNC_DATA_MAX
import com.eiyooooo.adblink.adb.LocalServices.generateSyncHeader
import com.eiyooooo.adblink.entity.FileInfo
import com.eiyooooo.adblink.entity.StatResult
import com.eiyooooo.adblink.entity.TransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Executes an ADB shell command.
 *
 * @param cmd The shell command to execute on the device
 * @return The output of the command as a string
 * @throws IOException If there is an error in communicating with the device
 */
suspend fun AdbConnection.runAdbCmd(cmd: String): String = withContext(Dispatchers.IO) {
    open(LocalServices.SHELL, cmd).use { stream ->
        val inputStream = stream.openInputStream()
        val reader = BufferedReader(InputStreamReader(inputStream))

        val output = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        output.toString().trim()
    }
}

/**
 * Gets file status information using stat system call (follows symbolic links).
 *
 * @param remotePath The path to the file on the device
 * @return A [StatResult] object containing the file status information
 * @throws IOException If there is an error in communicating with the device
 */
suspend fun AdbConnection.stat(remotePath: String): StatResult = withContext(Dispatchers.IO) {
    var syncStream: AdbStream? = null

    try {
        syncStream = open(LocalServices.SYNC)

        val useStatV2 = hasFeature("stat_v2")
        val statCommandId = if (useStatV2) ID_STAT_V2 else ID_LSTAT_V1

        syncStream.openOutputStream().use { os ->
            DataInputStream(syncStream.openInputStream()).use { dis ->
                val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
                val statHeader = generateSyncHeader(statCommandId, pathBytes.size)
                val statBuffer = ByteBuffer.allocate(statHeader.remaining() + pathBytes.size)
                statBuffer.put(statHeader)
                statBuffer.put(pathBytes)
                os.write(statBuffer.array())
                os.flush()

                if (useStatV2) {
                    val headerBytes = ByteArray(8)
                    dis.readFully(headerBytes)

                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val id = headerBuf.getInt()
                    val error = headerBuf.getInt()

                    if (id != ID_STAT_V2) {
                        throw IOException("Unexpected stat response ID: ${String.format("0x%08X", id)}")
                    }

                    if (error != 0) {
                        return@withContext StatResult(error = error)
                    }

                    val devBytes = ByteArray(8)
                    dis.readFully(devBytes)
                    val dev = ByteBuffer.wrap(devBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val inoBytes = ByteArray(8)
                    dis.readFully(inoBytes)
                    val ino = ByteBuffer.wrap(inoBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val modeBytes = ByteArray(4)
                    dis.readFully(modeBytes)
                    val mode = ByteBuffer.wrap(modeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val nlinkBytes = ByteArray(4)
                    dis.readFully(nlinkBytes)
                    val nlink = ByteBuffer.wrap(nlinkBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val uidBytes = ByteArray(4)
                    dis.readFully(uidBytes)
                    val uid = ByteBuffer.wrap(uidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val gidBytes = ByteArray(4)
                    dis.readFully(gidBytes)
                    val gid = ByteBuffer.wrap(gidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val sizeBytes = ByteArray(8)
                    dis.readFully(sizeBytes)
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val atimeBytes = ByteArray(8)
                    dis.readFully(atimeBytes)
                    val atime = ByteBuffer.wrap(atimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val mtimeBytes = ByteArray(8)
                    dis.readFully(mtimeBytes)
                    val mtime = ByteBuffer.wrap(mtimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val ctimeBytes = ByteArray(8)
                    dis.readFully(ctimeBytes)
                    val ctime = ByteBuffer.wrap(ctimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    return@withContext StatResult(
                        error = 0,
                        dev = dev,
                        ino = ino,
                        mode = mode,
                        nlink = nlink,
                        uid = uid,
                        gid = gid,
                        size = size,
                        atime = atime,
                        mtime = mtime,
                        ctime = ctime
                    )
                } else {
                    val headerBytes = ByteArray(16)
                    dis.readFully(headerBytes)

                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val id = headerBuf.getInt()

                    if (id != ID_LSTAT_V1) {
                        throw IOException("Unexpected stat response ID: ${String.format("0x%08X", id)}")
                    }

                    val mode = headerBuf.getInt()
                    val size = headerBuf.getInt().toLong() and 0xFFFFFFFFL
                    val time = headerBuf.getInt().toLong() and 0xFFFFFFFFL

                    if (mode == 0 && size == 0L && time == 0L) {
                        return@withContext StatResult(error = 2)  // ENOENT
                    }

                    return@withContext StatResult(
                        mode = mode,
                        size = size,
                        mtime = time,
                        ctime = time
                    )
                }
            }
        }
    } finally {
        try {
            syncStream?.close()
            Timber.d("Stat SYNC stream closed")
        } catch (e: IOException) {
            Timber.w(e, "Stat Error closing sync stream")
        }
    }
}

/**
 * Gets file status information using lstat system call (does not follow symbolic links).
 *
 * @param remotePath The path to the file on the device
 * @return A [StatResult] object containing the file status information
 * @throws IOException If there is an error in communicating with the device
 */
suspend fun AdbConnection.lstat(remotePath: String): StatResult = withContext(Dispatchers.IO) {
    var syncStream: AdbStream? = null

    try {
        syncStream = open(LocalServices.SYNC)

        val useStatV2 = hasFeature("stat_v2")
        val lstatCommandId = if (useStatV2) ID_LSTAT_V2 else ID_LSTAT_V1

        syncStream.openOutputStream().use { os ->
            DataInputStream(syncStream.openInputStream()).use { dis ->
                val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
                val lstatHeader = generateSyncHeader(lstatCommandId, pathBytes.size)
                val lstatBuffer = ByteBuffer.allocate(lstatHeader.remaining() + pathBytes.size)
                lstatBuffer.put(lstatHeader)
                lstatBuffer.put(pathBytes)
                os.write(lstatBuffer.array())
                os.flush()

                if (useStatV2) {
                    val headerBytes = ByteArray(8)
                    dis.readFully(headerBytes)

                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val id = headerBuf.getInt()
                    val error = headerBuf.getInt()

                    if (id != ID_LSTAT_V2) {
                        throw IOException("Unexpected lstat response ID: ${String.format("0x%08X", id)}")
                    }

                    if (error != 0) {
                        return@withContext StatResult(error = error)
                    }

                    val devBytes = ByteArray(8)
                    dis.readFully(devBytes)
                    val dev = ByteBuffer.wrap(devBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val inoBytes = ByteArray(8)
                    dis.readFully(inoBytes)
                    val ino = ByteBuffer.wrap(inoBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val modeBytes = ByteArray(4)
                    dis.readFully(modeBytes)
                    val mode = ByteBuffer.wrap(modeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val nlinkBytes = ByteArray(4)
                    dis.readFully(nlinkBytes)
                    val nlink = ByteBuffer.wrap(nlinkBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val uidBytes = ByteArray(4)
                    dis.readFully(uidBytes)
                    val uid = ByteBuffer.wrap(uidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val gidBytes = ByteArray(4)
                    dis.readFully(gidBytes)
                    val gid = ByteBuffer.wrap(gidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    val sizeBytes = ByteArray(8)
                    dis.readFully(sizeBytes)
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val atimeBytes = ByteArray(8)
                    dis.readFully(atimeBytes)
                    val atime = ByteBuffer.wrap(atimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val mtimeBytes = ByteArray(8)
                    dis.readFully(mtimeBytes)
                    val mtime = ByteBuffer.wrap(mtimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    val ctimeBytes = ByteArray(8)
                    dis.readFully(ctimeBytes)
                    val ctime = ByteBuffer.wrap(ctimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                    return@withContext StatResult(
                        error = 0,
                        dev = dev,
                        ino = ino,
                        mode = mode,
                        nlink = nlink,
                        uid = uid,
                        gid = gid,
                        size = size,
                        atime = atime,
                        mtime = mtime,
                        ctime = ctime
                    )
                } else {
                    val headerBytes = ByteArray(16)
                    dis.readFully(headerBytes)

                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val id = headerBuf.getInt()

                    if (id != ID_LSTAT_V1) {
                        throw IOException("Unexpected lstat response ID: ${String.format("0x%08X", id)}")
                    }

                    val mode = headerBuf.getInt()
                    val size = headerBuf.getInt().toLong() and 0xFFFFFFFFL
                    val time = headerBuf.getInt().toLong() and 0xFFFFFFFFL

                    if (mode == 0 && size == 0L && time == 0L) {
                        return@withContext StatResult(error = 2)  // ENOENT
                    }

                    return@withContext StatResult(
                        mode = mode,
                        size = size,
                        mtime = time,
                        ctime = time
                    )
                }
            }
        }
    } finally {
        try {
            syncStream?.close()
            Timber.d("LStat SYNC stream closed")
        } catch (e: IOException) {
            Timber.w(e, "LStat Error closing sync stream")
        }
    }
}

/**
 * Uploads a file from the local system to the device.
 *
 * @param localFile The path to the source file on the local system
 * @param remotePath The destination path on the device
 * @param fileMode The file permissions to set (in octal format, default "0644")
 * @param progressCallback Optional callback to report transfer progress
 * @throws IOException If there is an error during the file transfer
 */
suspend fun AdbConnection.pushFile(
    localFile: String,
    remotePath: String,
    fileMode: String = "0644",
    progressCallback: ((TransferProgress) -> Unit)? = null
) = withContext(Dispatchers.IO) {
    var syncStream: AdbStream? = null
    val startTime = System.nanoTime()
    var totalBytesSent: Long = 0
    var lastUpdateTime = startTime
    var lastUpdateBytes: Long = 0

    try {
        syncStream = open(LocalServices.SYNC)

        val fileSize = File(localFile).length()

        syncStream.openOutputStream().use { os ->
            DataInputStream(syncStream.openInputStream()).use { dis ->
                val remotePathWithMode = "$remotePath,$fileMode"
                val pathBytes = remotePathWithMode.toByteArray(StandardCharsets.UTF_8)
                val sendHeader = generateSyncHeader(ID_SEND_V1, pathBytes.size)
                val sendBuffer = ByteBuffer.allocate(sendHeader.remaining() + pathBytes.size)
                sendBuffer.put(sendHeader)
                sendBuffer.put(pathBytes)
                os.write(sendBuffer.array())
                os.flush()

                val buffer = ByteArray(SYNC_DATA_MAX)
                var bytesRead: Int

                File(localFile).inputStream().use { fileInput ->
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        val dataHeader = generateSyncHeader(ID_DATA, bytesRead)
                        os.write(dataHeader.array())
                        os.write(buffer, 0, bytesRead)
                        os.flush()

                        totalBytesSent += bytesRead

                        val now = System.nanoTime()
                        val elapsedNanos = now - lastUpdateTime
                        if (elapsedNanos >= 100_000_000 || totalBytesSent == fileSize) {
                            val progress = (totalBytesSent * 100 / fileSize).toInt()
                            val instantSpeed = if (elapsedNanos > 0) {
                                val bytesDelta = totalBytesSent - lastUpdateBytes
                                (bytesDelta * 1_000_000_000.0) / elapsedNanos / (1024.0 * 1024.0)
                            } else 0.0

                            progressCallback?.invoke(
                                TransferProgress(
                                    bytesTransferred = totalBytesSent,
                                    totalBytes = fileSize,
                                    speedMBps = instantSpeed,
                                    progress = progress
                                )
                            )

                            lastUpdateTime = now
                            lastUpdateBytes = totalBytesSent
                        }
                    }
                }

                val timestamp = System.currentTimeMillis() / 1000
                val doneHeader = generateSyncHeader(ID_DONE, timestamp.toInt())
                os.write(doneHeader.array())
                os.flush()

                val responseBytes = ByteArray(8)
                dis.readFully(responseBytes)
                val responseBuf = ByteBuffer.wrap(responseBytes).order(ByteOrder.LITTLE_ENDIAN)
                val responseId = responseBuf.getInt()
                val responseArg = responseBuf.getInt()

                when (responseId) {
                    ID_OKAY -> {
                        Timber.d("Push Received OKAY")
                    }

                    ID_FAIL -> {
                        val messageBytes = ByteArray(responseArg)
                        dis.readFully(messageBytes)
                        val errorMessage = String(messageBytes, StandardCharsets.UTF_8)
                        throw IOException("Push ADB FAIL response: $errorMessage")
                    }

                    else -> {
                        throw IOException("Push Unexpected sync response ID: ${String.format("0x%08X", responseId)}")
                    }
                }

                val quitBuffer = generateSyncHeader(ID_QUIT, 0)
                os.write(quitBuffer.array())
                os.flush()
                Timber.d("Push Sent QUIT")
            }
        }
    } finally {
        try {
            syncStream?.close()
            Timber.d("Push SYNC stream closed")
        } catch (e: IOException) {
            Timber.w(e, "Push Error closing sync stream")
        }
    }
}

/**
 * Downloads a file from the device to the local system.
 *
 * @param remotePath The path to the source file on the device
 * @param localFile The destination path on the local system, or null to download without saving
 * @param progressCallback Optional callback to report transfer progress
 * @return The total number of bytes read
 * @throws IOException If there is an error during the file transfer
 */
suspend fun AdbConnection.pullFile(
    remotePath: String,
    localFile: String? = null,
    progressCallback: ((TransferProgress) -> Unit)? = null
) = withContext(Dispatchers.IO) {
    var syncStream: AdbStream? = null
    val startTime = System.nanoTime()
    var totalBytesRead: Long = 0
    var lastUpdateTime = startTime
    var lastUpdateBytes: Long = 0

    try {
        val fileInfo = try {
            stat(remotePath)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get file size for progress reporting")
            null
        }

        val fileSize: Long = fileInfo?.size ?: -1

        syncStream = open(LocalServices.SYNC)

        val outputStream = localFile?.let { File(it).outputStream() }

        syncStream.openOutputStream().use { os ->
            DataInputStream(syncStream.openInputStream()).use { dis ->
                val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
                val recvHeader = generateSyncHeader(ID_RECV_V1, pathBytes.size)
                val recvBuffer = ByteBuffer.allocate(recvHeader.remaining() + pathBytes.size)
                recvBuffer.put(recvHeader)
                recvBuffer.put(pathBytes)
                os.write(recvBuffer.array())
                os.flush()

                val buffer = ByteArray(65536)


                while (true) {
                    val headerBytes = ByteArray(8)
                    try {
                        dis.readFully(headerBytes)
                    } catch (e: EOFException) {
                        Timber.w("Pull Stream unexpectedly ended while reading header")
                        break
                    }
                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val id = headerBuf.getInt()
                    val lengthOrTimestamp = headerBuf.getInt()

                    when (id) {
                        ID_DATA -> {
                            if (lengthOrTimestamp < 0 || lengthOrTimestamp > SYNC_DATA_MAX) {
                                throw IOException("Pull Invalid DATA block size: $lengthOrTimestamp (max: $SYNC_DATA_MAX)")
                            }
                            var remaining = lengthOrTimestamp
                            while (remaining > 0) {
                                val toRead = min(buffer.size, remaining)
                                val read = dis.read(buffer, 0, toRead)
                                if (read == -1) throw EOFException("Pull Stream unexpectedly ended while reading DATA")
                                outputStream?.write(buffer, 0, read)
                                remaining -= read
                            }
                            totalBytesRead += lengthOrTimestamp

                            val now = System.nanoTime()
                            val elapsedNanos = now - lastUpdateTime

                            if (elapsedNanos >= 100_000_000) {
                                val bytesDelta = totalBytesRead - lastUpdateBytes
                                val instantSpeed = (bytesDelta * 1_000_000_000.0) / elapsedNanos / (1024.0 * 1024.0)

                                val progress = if (fileSize > 0) {
                                    (totalBytesRead * 100 / fileSize).toInt()
                                } else -1

                                progressCallback?.invoke(
                                    TransferProgress(
                                        bytesTransferred = totalBytesRead,
                                        totalBytes = fileSize,
                                        speedMBps = instantSpeed,
                                        progress = progress
                                    )
                                )

                                lastUpdateTime = now
                                lastUpdateBytes = totalBytesRead
                            }
                        }

                        ID_DONE -> {
                            Timber.d("Pull Received DONE (timestamp: $lengthOrTimestamp)")
                            break
                        }

                        ID_FAIL -> {
                            if (lengthOrTimestamp < 0 || lengthOrTimestamp > 1024) {
                                throw IOException("Pull Invalid FAIL message size: $lengthOrTimestamp")
                            }
                            val messageBytes = ByteArray(lengthOrTimestamp)
                            dis.readFully(messageBytes)
                            val errorMessage = String(messageBytes, StandardCharsets.UTF_8)
                            throw IOException("Pull ADB FAIL response: $errorMessage")
                        }

                        0 -> {
                            Timber.w("Pull Received all-zero header, possibly after DONE")
                            break
                        }

                        else -> {
                            throw IOException("Pull Unexpected sync response ID: ${String.format("0x%08X", id)}")
                        }
                    }
                }

                if (progressCallback != null) {
                    val now = System.nanoTime()
                    val elapsedNanos = now - lastUpdateTime
                    val instantSpeed = if (elapsedNanos > 0) {
                        val bytesDelta = totalBytesRead - lastUpdateBytes
                        (bytesDelta * 1_000_000_000.0) / elapsedNanos / (1024.0 * 1024.0)
                    } else 0.0

                    val progress = if (fileSize > 0) {
                        (totalBytesRead * 100 / fileSize).toInt()
                    } else -1

                    progressCallback.invoke(
                        TransferProgress(
                            bytesTransferred = totalBytesRead,
                            totalBytes = fileSize,
                            speedMBps = instantSpeed,
                            progress = progress
                        )
                    )
                }

                val quitBuffer = generateSyncHeader(ID_QUIT, 0)
                try {
                    os.write(quitBuffer.array())
                    os.flush()
                    Timber.d("Pull Sent QUIT")
                } catch (e: IOException) {
                    Timber.w(e, "Pull Error sending QUIT (stream may be closed)")
                }
            }
        }

        outputStream?.close()
    } finally {
        try {
            syncStream?.close()
            Timber.d("Pull SYNC stream closed")
        } catch (e: IOException) {
            Timber.w(e, "Pull Error closing sync stream")
        }
    }
}

/**
 * Lists files in a directory on the device.
 *
 * @param remotePath The path to the directory on the device
 * @return A list of [FileInfo] objects representing the files in the directory
 * @throws IOException If there is an error in communicating with the device
 */
suspend fun AdbConnection.listFiles(
    remotePath: String
): List<FileInfo> = withContext(Dispatchers.IO) {
    var syncStream: AdbStream? = null
    val fileList = mutableListOf<FileInfo>()

    try {
        syncStream = open(LocalServices.SYNC)

        val useV2 = hasFeature("ls_v2")
        val listCommandId = if (useV2) ID_LIST_V2 else ID_LIST_V1

        syncStream.openOutputStream().use { os ->
            DataInputStream(syncStream.openInputStream()).use { dis ->
                val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
                val listHeader = generateSyncHeader(listCommandId, pathBytes.size)
                val listBuffer = ByteBuffer.allocate(listHeader.remaining() + pathBytes.size)
                listBuffer.put(listHeader)
                listBuffer.put(pathBytes)
                os.write(listBuffer.array())
                os.flush()

                while (true) {
                    val headerBytes = ByteArray(8)
                    try {
                        dis.readFully(headerBytes)
                    } catch (e: EOFException) {
                        Timber.w("List Stream unexpectedly ended while reading header")
                        break
                    }

                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    when (val id = headerBuf.getInt()) {
                        ID_DENT_V1 -> {
                            val mode = headerBuf.getInt()

                            val sizeBytes = ByteArray(4)
                            dis.readFully(sizeBytes)
                            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL

                            val timeBytes = ByteArray(4)
                            dis.readFully(timeBytes)
                            val lastModified = ByteBuffer.wrap(timeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL

                            val nameLengthBytes = ByteArray(4)
                            dis.readFully(nameLengthBytes)
                            val nameLength = ByteBuffer.wrap(nameLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            if (nameLength <= 0 || nameLength > 1024) {
                                throw IOException("Invalid filename length: $nameLength")
                            }

                            val nameBytes = ByteArray(nameLength)
                            dis.readFully(nameBytes)
                            val name = String(nameBytes, StandardCharsets.UTF_8)

                            fileList.add(FileInfo(mode, size, lastModified, name))
                        }

                        ID_DENT_V2 -> {
                            val error = headerBuf.getInt()

                            val devBytes = ByteArray(8)
                            dis.readFully(devBytes)
                            // val dev = ByteBuffer.wrap(devBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val inoBytes = ByteArray(8)
                            dis.readFully(inoBytes)
                            // val ino = ByteBuffer.wrap(inoBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val modeBytes = ByteArray(4)
                            dis.readFully(modeBytes)
                            val mode = ByteBuffer.wrap(modeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            val nlinkBytes = ByteArray(4)
                            dis.readFully(nlinkBytes)
                            // val nlink = ByteBuffer.wrap(nlinkBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            val uidBytes = ByteArray(4)
                            dis.readFully(uidBytes)
                            // val uid = ByteBuffer.wrap(uidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            val gidBytes = ByteArray(4)
                            dis.readFully(gidBytes)
                            // val gid = ByteBuffer.wrap(gidBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            val sizeBytes = ByteArray(8)
                            dis.readFully(sizeBytes)
                            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val atimeBytes = ByteArray(8)
                            dis.readFully(atimeBytes)
                            // val atime = ByteBuffer.wrap(atimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val mtimeBytes = ByteArray(8)
                            dis.readFully(mtimeBytes)
                            val lastModified = ByteBuffer.wrap(mtimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val ctimeBytes = ByteArray(8)
                            dis.readFully(ctimeBytes)
                            // val ctime = ByteBuffer.wrap(ctimeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong()

                            val nameLengthBytes = ByteArray(4)
                            dis.readFully(nameLengthBytes)
                            val nameLength = ByteBuffer.wrap(nameLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                            if (nameLength <= 0 || nameLength > 1024) {
                                throw IOException("Invalid filename length: $nameLength")
                            }

                            val nameBytes = ByteArray(nameLength)
                            dis.readFully(nameBytes)
                            val name = String(nameBytes, StandardCharsets.UTF_8)

                            if (error != 0) {
                                Timber.w("File entry error for $name: $error")
                                // If there is an error, we still add this file, but the user should know it may be unreliable.
                            }

                            fileList.add(FileInfo(mode, size, lastModified, name))
                        }

                        ID_DONE -> {
                            Timber.d("List Received DONE")
                            break
                        }

                        ID_FAIL -> {
                            val lengthOrArg = headerBuf.getInt()
                            if (lengthOrArg < 0 || lengthOrArg > 1024) {
                                throw IOException("List Invalid FAIL message size: $lengthOrArg")
                            }
                            val messageBytes = ByteArray(lengthOrArg)
                            dis.readFully(messageBytes)
                            val errorMessage = String(messageBytes, StandardCharsets.UTF_8)
                            throw IOException("List ADB FAIL response: $errorMessage")
                        }

                        else -> {
                            throw IOException("List Unexpected sync response ID: ${String.format("0x%08X", id)}")
                        }
                    }
                }

                val quitBuffer = generateSyncHeader(ID_QUIT, 0)
                try {
                    os.write(quitBuffer.array())
                    os.flush()
                    Timber.d("List Sent QUIT")
                } catch (e: IOException) {
                    Timber.w(e, "List Error sending QUIT (stream may be closed)")
                }
            }
        }
    } finally {
        try {
            syncStream?.close()
            Timber.d("List SYNC stream closed")
        } catch (e: IOException) {
            Timber.w(e, "List Error closing sync stream")
        }
    }

    fileList
}
