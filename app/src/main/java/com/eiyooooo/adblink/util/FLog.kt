package com.eiyooooo.adblink.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eiyooooo.adblink.BuildConfig
import com.eiyooooo.adblink.MyApplication.Companion.appStartTime
import com.eiyooooo.adblink.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FLog {

    const val PREFIX = "[ADBLink ${BuildConfig.VERSION_NAME}]-> "

    private var logFile: File? = null
    private var fLogTree = FLogTree()

    private val logChannel = Channel<String>(Channel.UNLIMITED)
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var writerJob: Job? = null

    @SuppressLint("LogNotTimber")
    fun init(context: Context) {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()

        val oldLogFiles = logsDir.listFiles { file ->
            file.isFile && file.name.startsWith("ADBLink-") && file.name.endsWith(".txt")
        }
        logFile = File(logsDir, "ADBLink-${BuildConfig.VERSION_NAME}-${appStartTime.time}.txt")

        if (oldLogFiles != null && oldLogFiles.isNotEmpty()) {
            val sortedLogFiles = oldLogFiles.sortedBy { file ->
                val regex = "ADBLink-.*-(\\d+)\\.txt".toRegex()
                val match = regex.find(file.name)
                match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }

            FileWriter(logFile, true).use { writer ->
                val buffer = CharArray(8192)
                sortedLogFiles.forEach { file ->
                    try {
                        if (file.exists()) {
                            FileReader(file).use { reader ->
                                var charsRead: Int
                                while (reader.read(buffer).also { charsRead = it } > 0) {
                                    writer.write(buffer, 0, charsRead)
                                }
                                writer.write("\n")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FLog", "Error reading log file ${file.name}", e)
                    }
                }
            }

            oldLogFiles.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    private fun append(logDateFormat: SimpleDateFormat, priority: Int, tag: String?, message: String) {
        if (writerJob?.isActive != true) return

        val timeStamp = logDateFormat.format(Date())
        val priorityStr = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "A"
        }
        val logTag = tag ?: "NoTag"
        val logMessage = "$PREFIX $timeStamp [$priorityStr/$logTag]: $message\n"

        logScope.launch {
            logChannel.send(logMessage)
        }
    }

    private val writeImmediately: MutableStateFlow<Boolean> by lazy { MutableStateFlow(false) }

    @SuppressLint("LogNotTimber")
    fun start() {
        if (!Timber.forest().contains(fLogTree)) {
            Timber.plant(fLogTree)
        }

        if (writerJob?.isActive == true) return
        writerJob = logScope.launch {
            writeToFile(listOf(""))
            Timber.i("FLog started")
            while (isActive) {
                try {
                    val logBuffer = mutableListOf<String>()
                    var messageCount = 0

                    withTimeoutOrNull(1000L) {
                        for (logMessage in logChannel) {
                            if (writeImmediately.value) break
                            logBuffer.add(logMessage)
                            messageCount++
                            if (messageCount >= 10) break
                        }
                    }

                    if (logBuffer.isNotEmpty()) {
                        writeToFile(logBuffer)
                    }

                    writeImmediately.update { false }
                } catch (t: Throwable) {
                    Log.e("FLog", "Error in log writer coroutine", t)
                    delay(1000)
                }
            }
        }
    }

    fun stop() {
        if (Timber.forest().contains(fLogTree)) {
            Timber.uproot(fLogTree)
        }

        writerJob?.cancel()
        writerJob = null
    }

    private suspend fun writeLast() {
        writeImmediately.update { true }
        logChannel.send("")
        writeImmediately.first { !it }
    }

    @SuppressLint("LogNotTimber")
    private suspend fun writeToFile(logBuffer: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                FileWriter(logFile, true).use { writer ->
                    for (logMessage in logBuffer) {
                        writer.write(logMessage)
                    }
                    writer.flush()
                    writer.close()
                }
            } catch (t: Throwable) {
                Log.e("FLog", "Error writing log to file", t)
            }
        }
    }

    suspend fun read(): String? {
        return withContext(Dispatchers.IO) {
            writeLast()
            logFile?.let {
                if (it.exists()) {
                    it.readText()
                } else null
            }
        }
    }

    fun export(context: Context, showSnackbar: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (context is Activity) {
                    ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                }
                showSnackbar(context.getString(R.string.storage_permission_required))
                return
            }
        }

        logScope.launch {
            try {
                writeLast()
                val sourceFile = logFile ?: throw IllegalStateException("Log file not found")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/ADBLink")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { outputUri ->
                        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                            FileInputStream(sourceFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        showSnackbar(context.getString(R.string.log_export_success))
                    }
                } else {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val adbLinkDir = File(downloadDir, "ADBLink")
                    if (!adbLinkDir.exists()) {
                        adbLinkDir.mkdirs()
                    }
                    val destinationFile = File(adbLinkDir, sourceFile.name)
                    sourceFile.copyTo(destinationFile, overwrite = true)
                    showSnackbar(context.getString(R.string.log_export_success))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error occurred while exporting logs")
                showSnackbar(context.getString(R.string.log_export_failed, e.message))
            }
        }
    }

    class FLogTree : Timber.DebugTree() {

        private val logDateFormat = SimpleDateFormat("MM-dd hh:mm:ss.SSS", Locale.getDefault())

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag, PREFIX + message, t)
            append(logDateFormat, priority, tag, message)
        }
    }
}
