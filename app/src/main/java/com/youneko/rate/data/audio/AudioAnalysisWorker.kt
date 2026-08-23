package com.youneko.rate.data.audio

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youneko.rate.R
import com.youneko.rate.data.CrashLogStore
import android.util.Log
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioAnalysisWorkerEntryPoint {
    fun audioAnalysisDao(): AudioAnalysisDao
}

class AudioAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val analyzeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashLogStore.write(applicationContext, Thread.currentThread(), throwable)
        Log.e("ANALYZE", "ANALYZE: uncaught coroutine exception", throwable)
    }

    override suspend fun doWork(): Result = withContext(analyzeExceptionHandler) {
        doWorkInternal()
    }

    private suspend fun doWorkInternal(): Result {
        val uri = inputData.getString(KEY_URI) ?: return Result.failure(workDataOf(KEY_ERROR to "Thiếu URI file audio"))
        val fileName = inputData.getString(KEY_FILE_NAME).orEmpty().ifBlank { uri.substringAfterLast('/').ifBlank { "audio" } }
        val fileIndex = inputData.getInt(KEY_FILE_INDEX, 1)
        val totalFiles = inputData.getInt(KEY_TOTAL_FILES, 1)
        setForegroundSafely(createForegroundInfo(fileName, fileIndex, totalFiles, 0f))
        return try {
            val source = AudioSourceInspector.inspect(applicationContext, Uri.parse(uri))
            Log.d("ANALYZE", "preflight displayName=${source.displayName} declaredMime=${source.declaredMime} trackMime=${source.trackMime} size=${source.sizeBytes}")
            publish(AudioAnalysisProgress(AudioAnalysisStep.READING_HEADER, 0f), fileName, fileIndex, totalFiles)
            val result = StreamingAudioAnalysisEngine(applicationContext).analyze(
                uriString = uri,
                trackId = inputData.getString(KEY_TRACK_ID),
                albumId = inputData.getString(KEY_ALBUM_ID),
                onEvent = { event ->
                    when (event) {
                        is SpectrogramEvent.Header -> publish(
                            AudioAnalysisProgress(AudioAnalysisStep.READING_HEADER, 1f),
                            fileName,
                            fileIndex,
                            totalFiles,
                        )
                        is SpectrogramEvent.Column -> publish(
                            AudioAnalysisProgress(AudioAnalysisStep.FFT, event.progress),
                            fileName,
                            fileIndex,
                            totalFiles,
                        )
                        is SpectrogramEvent.Progress -> publish(
                            AudioAnalysisProgress(AudioAnalysisStep.DECODING, event.progress),
                            fileName,
                            fileIndex,
                            totalFiles,
                        )
                        is SpectrogramEvent.Completed -> Unit
                    }
                },
                shouldStop = { isStopped },
            )
            publish(AudioAnalysisProgress(AudioAnalysisStep.SAVING, 0f), fileName, fileIndex, totalFiles)
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AudioAnalysisWorkerEntryPoint::class.java)
            entryPoint.audioAnalysisDao().upsert(result)
            publish(AudioAnalysisProgress(AudioAnalysisStep.SAVING, 1f), fileName, fileIndex, totalFiles)
            Result.success(workDataOf(KEY_ANALYSIS_ID to result.id))
        } catch (cancelled: CancellationException) {
            Result.failure(workDataOf(KEY_CANCELLED to true, KEY_ERROR to (cancelled.message ?: "Đã huỷ phân tích")))
        } catch (error: OutOfMemoryError) {
            val message = classifyAnalyzeError(error)
            CrashLogStore.write(applicationContext, Thread.currentThread(), error)
            Log.e("ANALYZE", "ANALYZE: uri=$uri err=$message", error)
            Result.failure(workDataOf(KEY_ERROR to message, KEY_FILE_NAME to fileName))
        } catch (error: Throwable) {
            val message = classifyAnalyzeError(error)
            CrashLogStore.write(applicationContext, Thread.currentThread(), error)
            Log.e("ANALYZE", "ANALYZE: uri=$uri mime=? codec=? sampleRate=? ch=? pcmEnc=? err=$message", error)
            if (runAttemptCount < 2 && !isStopped && !error.isPermanentAnalyzeError()) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to message, KEY_FILE_NAME to fileName))
        }
    }

    @SuppressLint("NewApi")
    private suspend fun setForegroundSafely(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.w("ANALYZE", "ANALYZE: foreground start not allowed; continuing without notification", error)
        } catch (error: SecurityException) {
            Log.w("ANALYZE", "ANALYZE: notification permission unavailable; continuing without notification", error)
        } catch (error: IllegalStateException) {
            Log.w("ANALYZE", "ANALYZE: foreground promotion unavailable; continuing without notification", error)
        }
    }

    private fun classifyAnalyzeError(error: Throwable): String = when (error) {
        is AnalyzeInputException.AccessDenied -> "Không có quyền truy cập tệp"
        is AnalyzeInputException.NoDecoder -> "Thiết bị không có bộ giải mã cho định dạng ${error.mime}"
        is AnalyzeInputException.UnsupportedFormat -> "Không hỗ trợ định dạng ${error.mime}"
        is OutOfMemoryError -> "Hết bộ nhớ khi phân tích tệp dài"
        is SecurityException -> "Không có quyền truy cập tệp"
        is IllegalArgumentException, is IllegalStateException -> "Tệp bị hỏng hoặc không đọc được"
        else -> "Không phân tích được (${error::class.simpleName ?: "lỗi không xác định"})"
    }.removePrefix(": ").ifBlank { "Tệp bị hỏng hoặc không đọc được" }

    private fun Throwable.isPermanentAnalyzeError(): Boolean = this is AnalyzeInputException || this is SecurityException || this is OutOfMemoryError

    private suspend fun publish(progress: AudioAnalysisProgress, fileName: String, index: Int, total: Int) {
        if (isStopped) throw CancellationException("Đã huỷ phân tích")
        val data = workDataOf(
            KEY_FILE_NAME to fileName,
            KEY_FILE_INDEX to index,
            KEY_TOTAL_FILES to total,
            KEY_STEP to progress.step.ordinal,
            KEY_STEP_PROGRESS to progress.stepProgress,
            KEY_PROGRESS to (progress.stepProgress.coerceIn(0f, 1f) * 100f).toInt(),
        )
        setProgressAsync(data)
        setForegroundSafely(createForegroundInfo(fileName, index, total, progress.stepProgress))
    }

    private fun createForegroundInfo(fileName: String, index: Int, total: Int, progress: Float): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.audio_analysis_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val cancelIntent = Intent(applicationContext, AudioAnalysisCancelReceiver::class.java).setAction(ACTION_CANCEL)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            NOTIFICATION_ID,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cat_cover)
            .setContentTitle(applicationContext.getString(R.string.audio_analysis_notification_title, index, total))
            .setContentText(fileName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress.coerceIn(0f, 1f) * 100).toInt(), progress < 0f)
            .addAction(R.drawable.ic_cat_cover, applicationContext.getString(R.string.audio_analysis_cancel), cancelPendingIntent)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK = "audio-quality-analysis"
        const val ACTION_CANCEL = "com.youneko.rate.action.CANCEL_AUDIO_ANALYSIS"
        const val KEY_URI = "audio_uri"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_INDEX = "file_index"
        const val KEY_TOTAL_FILES = "total_files"
        const val KEY_STEP = "step"
        const val KEY_STEP_PROGRESS = "step_progress"
        const val KEY_PROGRESS = "progress"
        const val KEY_ANALYSIS_ID = "analysis_id"
        const val KEY_ERROR = "error"
        const val KEY_CANCELLED = "cancelled"
        private const val CHANNEL_ID = "audio-analysis"
        private const val NOTIFICATION_ID = 8208
    }
}

class AudioAnalysisCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AudioAnalysisWorker.ACTION_CANCEL) {
            WorkManager.getInstance(context).cancelUniqueWork(AudioAnalysisWorker.UNIQUE_WORK)
        }
    }
}
