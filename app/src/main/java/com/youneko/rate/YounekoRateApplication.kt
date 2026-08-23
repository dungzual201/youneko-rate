package com.youneko.rate

import android.app.Application
import com.youneko.rate.data.CrashLogStore
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YounekoRateApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogStore.write(this, thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
