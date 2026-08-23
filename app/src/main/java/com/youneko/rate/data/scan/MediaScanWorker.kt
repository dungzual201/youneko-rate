package com.youneko.rate.data.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.app.ForegroundServiceStartNotAllowedException
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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
import com.youneko.rate.data.CrashLogStore
import dagger.hilt.EntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "media_scan"
private const val SCAN_TAG = "SCAN"
private const val NOTIFICATION_ID = 4101
private const val UNIQUE_PERIODIC = "media_scan_periodic"
const val UNIQUE_ON_RESUME = "media_scan_on_resume"

enum class ScanPhase { METADATA, ARTWORK }

data class ScanState(
    val phase: ScanPhase,
    val done: Int,
    val total: Int,
) {
    val fraction: Float?
        get() = total.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaScanWorkerEntryPoint {
    fun scanner(): MediaStoreScanner
}

class MediaStoreScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val scanExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashLogStore.write(applicationContext, Thread.currentThread(), throwable)
        Log.e(SCAN_TAG, "SCAN: uncaught coroutine exception", throwable)
    }

    override suspend fun doWork(): Result = withContext(scanExceptionHandler) {
        doWorkInternal()
    }

    private suspend fun doWorkInternal(): Result {
        if (!MediaStoreScanWorker.PROCESS_GATE.compareAndSet(false, true)) {
            Log.i(SCAN_TAG, "SCAN: WorkInfo.State=SUCCEEDED exception=null reason=already-running")
            return Result.success(workDataOf(MediaStoreScanWorker.KEY_SKIPPED to true))
        }
        return try {
            Log.i(SCAN_TAG, "SCAN: WorkInfo.State=RUNNING exception=null")
            val scanner = EntryPointAccessors.fromApplication(applicationContext, MediaScanWorkerEntryPoint::class.java).scanner()
            if (!hasPermission() && !scanner.hasSafRoots()) {
                Log.i(SCAN_TAG, "SCAN: WorkInfo.State=SUCCEEDED exception=null reason=no-access")
                Result.success(workDataOf(MediaStoreScanWorker.KEY_SKIPPED to true))
            } else {
                setForegroundSafely(0, 0)
                runCatching {
                    scanner.dedupeIfNeeded()
                    val forceFull = inputData.getBoolean(KEY_FORCE_FULL, false)
                    var phase = ScanPhase.METADATA
                    setProgressAsync(progressData(phase, 0, 0))
                    val result = scanner.scan(
                        forceFull,
                        onProgress = { done, total ->
                            setProgressAsync(progressData(phase, done, total))
                            runCatching { setForegroundAsync(createForegroundInfo(done, total)) }
                                .onFailure { Log.w(SCAN_TAG, "SCAN: progress notification skipped: ${it.message}") }
                        },
                        onPhaseChanged = { nextPhase ->
                            phase = nextPhase
                            setProgressAsync(progressData(phase, 0, 0))
                        },
                    )
                    val safResult = scanner.scanSafRoots { done, total ->
                        setProgressAsync(progressData(phase, done, total))
                        runCatching { setForegroundAsync(createForegroundInfo(done, total)) }
                            .onFailure { Log.w(SCAN_TAG, "SCAN: progress notification skipped: ${it.message}") }
                    }
                    Log.i(SCAN_TAG, "SCAN: WorkInfo.State=SUCCEEDED exception=null")
                    Result.success(workDataOf(KEY_SCANNED to result.scanned + safResult.scanned, KEY_ADDED to result.added + safResult.added, KEY_MISSING to result.missing + safResult.missing, KEY_SKIPPED to (result.skipped && safResult.scanned == 0)))
                }.getOrElse { throwable ->
                    val nextState = if (runAttemptCount < 2) "RETRY" else "FAILED"
                    Log.e(SCAN_TAG, "SCAN: WorkInfo.State=$nextState exception=${throwable::class.java.simpleName}: ${throwable.message}", throwable)
                    CrashLogStore.write(applicationContext, Thread.currentThread(), throwable)
                    if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to throwable.message.orEmpty()))
                }
            }
        } finally {
            MediaStoreScanWorker.PROCESS_GATE.set(false)
        }
    }

    private fun progressData(phase: ScanPhase, done: Int, total: Int): Data =
        Data.Builder()
            .putString(KEY_PHASE, phase.name)
            .putInt(KEY_DONE, done)
            .putInt(KEY_TOTAL, total)
            .build()

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("NewApi")
    private suspend fun setForegroundSafely(done: Int, total: Int) {
        try {
            setForeground(createForegroundInfo(done, total))
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.w(SCAN_TAG, "SCAN: foreground start not allowed; continuing without notification", error)
        } catch (error: SecurityException) {
            Log.w(SCAN_TAG, "SCAN: foreground notification permission unavailable; continuing without notification", error)
        } catch (error: IllegalStateException) {
            Log.w(SCAN_TAG, "SCAN: foreground promotion unavailable; continuing without notification", error)
        }
    }

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.media_scan_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_paw)
            .setContentTitle(applicationContext.getString(R.string.media_scan_notification_title))
            .setContentText(applicationContext.getString(R.string.media_scan_progress, done, total))
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private val PROCESS_GATE = AtomicBoolean(false)
        const val KEY_FORCE_FULL = "force_full"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SCANNED = "scanned"
        const val KEY_ADDED = "added"
        const val KEY_MISSING = "missing"
        const val KEY_SKIPPED = "skipped"
        const val KEY_ERROR = "error"
    }
}

fun startScan(context: Context, forceFull: Boolean = false, trigger: String) {
    val alreadyRunning = runCatching {
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_ON_RESUME).get(250L, TimeUnit.MILLISECONDS)
            .any { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }
    }.getOrDefault(false)
    Log.d(SCAN_TAG, "startScan called, alreadyRunning=$alreadyRunning, trigger=$trigger")
    if (alreadyRunning) return
    val request = OneTimeWorkRequestBuilder<MediaStoreScanWorker>()
        .setInputData(workDataOf(MediaStoreScanWorker.KEY_FORCE_FULL to forceFull))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_ON_RESUME, ExistingWorkPolicy.KEEP, request)
}

fun enqueueMediaScan(context: Context, forceFull: Boolean = false) = startScan(context, forceFull, "legacy-enqueue")

fun ensureMediaScan(context: Context) = startScan(context, trigger = "activity-resume")

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
    private var attachedOwner: LifecycleOwner? = null
    private var observedUris: List<android.net.Uri> = emptyList()
    private val observer = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacksAndMessages(this)
            handler.postDelayed({ startScan(context, trigger = "media-observer") }, 2_000L)
        }
    }

    fun attach(owner: LifecycleOwner) {
        if (attachedOwner === owner) return
        attachedOwner?.lifecycle?.removeObserver(this)
        unregisterObservers()
        attachedOwner = owner
        owner.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        observedUris = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.getExternalVolumeNames(context).map { volume -> MediaStore.Audio.Media.getContentUri(volume) }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }.ifEmpty { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
        observedUris.forEach { uri -> context.contentResolver.registerContentObserver(uri, true, observer) }
        ensureMediaScan(context)
        schedulePeriodicMediaScan(context)
    }

    override fun onStop(owner: LifecycleOwner) {
        unregisterObservers()
    }

    private fun unregisterObservers() {
        if (observedUris.isNotEmpty()) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        observedUris = emptyList()
        handler.removeCallbacksAndMessages(observer)
    }
}
