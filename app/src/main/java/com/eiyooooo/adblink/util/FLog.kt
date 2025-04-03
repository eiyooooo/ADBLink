package com.eiyooooo.adblink.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
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

    fun init(context: Context) {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        val logFiles = logsDir.listFiles { _, name -> name.endsWith(".log") }
        logFiles?.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        logFile = File(logsDir, "ADBLink-${BuildConfig.VERSION_NAME}-${appStartTime.time}.log")
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
        val logMessage = "$timeStamp [$priorityStr/$logTag]: $message\n"

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

    fun export(context: Context) {
        logScope.launch {
            try {
                val fileUri = logFile?.let {
                    writeLast()
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                } ?: throw IllegalStateException("Log file not found")

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    type = "text/plain"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_logs)))
            } catch (e: Exception) {
                Timber.e(e, "Error occurred while exporting logs")
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
