package com.youneko.rate.data.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youneko.rate.R
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioAnalysisWorkerEntryPoint {
    fun audioAnalysisDao(): AudioAnalysisDao
}

class AudioAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI) ?: return Result.failure(workDataOf(KEY_ERROR to "Thiếu URI file audio"))
        setForeground(createForegroundInfo())
        return runCatching {
            setProgress(workDataOf(KEY_PROGRESS to 10))
            val result = AudioAnalysisEngine(applicationContext).analyze(
                uriString = uri,
                trackId = inputData.getString(KEY_TRACK_ID),
                albumId = inputData.getString(KEY_ALBUM_ID),
            )
            setProgress(workDataOf(KEY_PROGRESS to 90))
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AudioAnalysisWorkerEntryPoint::class.java)
            entryPoint.audioAnalysisDao().upsert(result)
            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success(workDataOf(KEY_ANALYSIS_ID to result.id))
        }.getOrElse { error ->
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to error.message.orEmpty()))
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Audio analysis", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cat_cover)
            .setContentTitle(applicationContext.getString(R.string.analyze))
            .setContentText(applicationContext.getString(R.string.audio_analysis_running))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_URI = "audio_uri"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ANALYSIS_ID = "analysis_id"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "audio-analysis"
        private const val NOTIFICATION_ID = 8208
    }
}
