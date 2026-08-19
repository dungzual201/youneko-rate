package com.youneko.rate.data.phase12

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.youneko.rate.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryMaintenanceEntryPoint {
    fun database(): com.youneko.rate.data.local.YounekoDatabase
}

class LibraryMaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val db = EntryPointAccessors.fromApplication(applicationContext, LibraryMaintenanceEntryPoint::class.java).database()
            val albums = db.albumDao().findAll()
            val duplicateGroups = albums.groupBy { it.artistId to it.title.trim().lowercase() }.filterValues { it.size > 1 }
            val orphanCovers = albums.filter { uri -> uri.coverUri?.startsWith("file://") == true && !File(uri.coverUri.removePrefix("file://")).exists() }
            val report = buildString {
                appendLine("duplicate_groups=${duplicateGroups.size}")
                duplicateGroups.forEach { (key, values) -> appendLine("duplicate=${key.first}|${key.second}|${values.joinToString { it.id }}") }
                appendLine("orphan_covers=${orphanCovers.size}")
                orphanCovers.forEach { appendLine("orphan_cover=${it.id}") }
            }
            File(applicationContext.cacheDir, "library-maintenance-report.txt").writeText(report)
            Result.success()
        }.getOrElse { Result.failure() }
    }
}

fun scheduleLibraryMaintenance(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("youneko-library-maintenance", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LibraryMaintenanceWorker>().build())
}

class InProgressAlbumWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = EntryPointAccessors.fromApplication(context, LibraryMaintenanceEntryPoint::class.java).database()
                val albums = db.albumDao().findAll()
                val tracks = db.trackDao().findAll().groupBy { it.albumId }
                val album = albums.firstOrNull { tracks[it.id].orEmpty().any { track -> track.stars == null } }
                val views = RemoteViews(context.packageName, R.layout.widget_in_progress)
                views.setTextViewText(R.id.widget_title, album?.title ?: context.getString(R.string.widget_no_in_progress))
                manager.updateAppWidget(ComponentName(context, InProgressAlbumWidgetProvider::class.java), views)
            } finally { pending.finish() }
        }
    }
}
