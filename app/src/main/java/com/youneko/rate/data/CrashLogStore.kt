package com.youneko.rate.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogStore {
    private const val TAG = "CRASH_LOG"
    private const val DIRECTORY = "crash"
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val versionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")
            val text = buildString {
                appendLine("Youneko Rate crash log")
                appendLine("time=$timestamp")
                appendLine("thread=${thread.name}")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("model=${Build.MODEL}")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("versionName=$versionName")
                appendLine()
                append(Log.getStackTraceString(throwable))
            }
            File(directory, "$PREFIX$timestamp$SUFFIX").writeText(text, Charsets.UTF_8)
        }.onFailure { Log.e(TAG, "Unable to persist crash log", it) }
    }

    fun latest(context: Context): File? = File(context.filesDir, DIRECTORY)
        .listFiles { file -> file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }
        ?.maxByOrNull { it.lastModified() }
}
