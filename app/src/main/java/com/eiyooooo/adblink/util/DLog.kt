package com.eiyooooo.adblink.util

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eiyooooo.adblink.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DLog {
    private val logs = mutableMapOf<String, StringBuilder>()

    fun log(id: String, log: String) {
        val logBuilder = logs.getOrPut(id) { StringBuilder() }
        logBuilder.append("<${DateFormat.format("HH:mm:ss", Date())}> ")
            .append(log)
            .append("\n")
    }

    fun log(id: String, throwable: Throwable) {
        log(id, Log.getStackTraceString(throwable))
    }

    fun logWithoutTime(id: String, log: String) {
        val logBuilder = logs.getOrPut(id) { StringBuilder() }
        logBuilder.append(log).append("\n")
    }

    fun logWithoutTime(id: String, throwable: Throwable) {
        logWithoutTime(id, Log.getStackTraceString(throwable))
    }

    fun getLogs(): String {
        val logBuilder = StringBuilder()
        val ids = mutableListOf<String>()
        // for (Device device : DeviceListAdapter.devicesList) {
        //     ids.add(device.id);
        // }

        logs.forEach { (key, value) ->
            if (!ids.contains(key)) {
                logBuilder.append(value)
            }
        }

        return if (logBuilder.isNotEmpty()) logBuilder.toString() else "no log found"
    }

    fun getLogs(id: String): String {
        return logs[id]?.toString() ?: "no log found"
    }

    fun clearLogs(id: String? = null) {
        if (id != null) {
            logs.remove(id)
        } else {
            logs.clear()
        }
    }

    fun getAllDeviceIds(): List<String> {
        return logs.keys.toList()
    }

    suspend fun export(context: Context, id: String, showSnackbar: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (context is Activity) {
                    ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                }
                showSnackbar(context.getString(R.string.storage_permission_required))
                return
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val logContent = getLogs(id)
                if (logContent == "no log found") {
                    showSnackbar(context.getString(R.string.log_export_failed, "无日志可导出"))
                    return@withContext
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ADBLink-Device-$id-$timestamp.txt"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/ADBLink")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { outputUri ->
                        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                            outputStream.write(logContent.toByteArray())
                        }
                        showSnackbar(context.getString(R.string.log_export_success))
                    }
                } else {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val adbLinkDir = File(downloadDir, "ADBLink")
                    if (!adbLinkDir.exists()) {
                        adbLinkDir.mkdirs()
                    }
                    val destinationFile = File(adbLinkDir, fileName)
                    FileOutputStream(destinationFile).use { outputStream ->
                        outputStream.write(logContent.toByteArray())
                    }
                    showSnackbar(context.getString(R.string.log_export_success))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error occurred while exporting device logs")
                showSnackbar(context.getString(R.string.log_export_failed, e.message))
            }
        }
    }
}
