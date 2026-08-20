package com.youneko.rate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dagger.hilt.android.AndroidEntryPoint
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.scan.MediaScanCoordinator
import com.youneko.rate.data.scan.MediaStoreScanner
import com.youneko.rate.data.export.reconcilePendingRestore
import com.youneko.rate.data.local.YounekoDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.youneko.rate.navigation.YounekoNavHost
import com.youneko.rate.ui.ThemeMode
import com.youneko.rate.ui.YounekoRateTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var mediaScanCoordinator: MediaScanCoordinator
    @Inject lateinit var database: YounekoDatabase
    @Inject lateinit var mediaStoreScanner: MediaStoreScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaScanCoordinator.attach(this)
        lifecycleScope.launch { reconcilePendingRestore(applicationContext, database, mediaStoreScanner) }
        setContent {
            val settings = remember { SettingsDataStore(applicationContext) }
            val dynamicColor by settings.dynamicColor.collectAsState(initial = false)
            val themeModeName by settings.themeMode.collectAsState(initial = "SYSTEM")
            YounekoRateTheme(dynamicColor = dynamicColor, themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)) {
                YounekoNavHost()
            }
        }
    }
}
