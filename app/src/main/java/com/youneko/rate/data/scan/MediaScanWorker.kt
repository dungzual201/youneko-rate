package com.youneko.rate.data.scan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youneko.rate.R
import dagger.hilt.EntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "media_scan"
private const val NOTIFICATION_ID = 4101
private const val UNIQUE_PERIODIC = "media_scan_periodic"
private const val UNIQUE_ON_RESUME = "media_scan_on_resume"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaScanWorkerEntryPoint {
    fun scanner(): MediaStoreScanner
}

class MediaStoreScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scanner = EntryPointAccessors.fromApplication(applicationContext, MediaScanWorkerEntryPoint::class.java).scanner()
        if (!hasPermission() && !scanner.hasSafRoots()) return Result.success(workDataOf(KEY_SKIPPED to true))
        setForeground(createForegroundInfo(0, 0))
        return runCatching {
            val forceFull = inputData.getBoolean(KEY_FORCE_FULL, false)
            val result = scanner.scan(forceFull) { done, total ->
                setProgressAsync(Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total).build())
                setForegroundAsync(createForegroundInfo(done, total))
            }
            val safResult = scanner.scanSafRoots { done, total ->
                setProgressAsync(Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total).build())
                setForegroundAsync(createForegroundInfo(done, total))
            }
            Result.success(workDataOf(KEY_SCANNED to result.scanned + safResult.scanned, KEY_ADDED to result.added + safResult.added, KEY_MISSING to result.missing + safResult.missing, KEY_SKIPPED to (result.skipped && safResult.scanned == 0)))
        }.getOrElse { throwable ->
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to throwable.message.orEmpty()))
        }
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.media_scan_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.media_scan_notification_title))
            .setContentText(applicationContext.getString(R.string.media_scan_progress, done, total))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_FORCE_FULL = "force_full"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SCANNED = "scanned"
        const val KEY_ADDED = "added"
        const val KEY_MISSING = "missing"
        const val KEY_SKIPPED = "skipped"
        const val KEY_ERROR = "error"
    }
}

fun enqueueMediaScan(context: Context, forceFull: Boolean = false) {
    val request = OneTimeWorkRequestBuilder<MediaStoreScanWorker>()
        .setInputData(workDataOf(MediaStoreScanWorker.KEY_FORCE_FULL to forceFull))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_ON_RESUME, ExistingWorkPolicy.REPLACE, request)
}

fun schedulePeriodicMediaScan(context: Context) {
    val request = PeriodicWorkRequestBuilder<MediaStoreScanWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
}

@Singleton
class MediaScanCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private val observer = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacksAndMessages(this)
            handler.postDelayed({ enqueueMediaScan(context) }, 2_000L)
        }
    }

    fun attach(owner: LifecycleOwner) {
        if (registered) return
        registered = true
        owner.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        enqueueMediaScan(context)
        schedulePeriodicMediaScan(context)
    }

    override fun onStop(owner: LifecycleOwner) {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        handler.removeCallbacksAndMessages(observer)
    }
}
