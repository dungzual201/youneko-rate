package com.youneko.rate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.export.reconcilePendingRestore
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.scan.MediaScanCoordinator
import com.youneko.rate.data.scan.MediaStoreScanner
import com.youneko.rate.navigation.YounekoNavHost
import com.youneko.rate.ui.ThemeMode
import com.youneko.rate.ui.YounekoRateTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityDependencies {
    fun mediaScanCoordinator(): MediaScanCoordinator
    fun database(): YounekoDatabase
    fun mediaStoreScanner(): MediaStoreScanner
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val dependencies by lazy(LazyThreadSafetyMode.NONE) {
        EntryPointAccessors.fromApplication(applicationContext, MainActivityDependencies::class.java)
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_NOTIFICATIONS_DECLINED, true).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dependencies.mediaScanCoordinator().attach(this)
        lifecycleScope.launch { reconcilePendingRestore(applicationContext, dependencies.database(), dependencies.mediaStoreScanner()) }
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean(KEY_NOTIFICATIONS_DECLINED, false)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings = androidx.compose.runtime.remember { SettingsDataStore(applicationContext) }
            val dynamicColor = settings.dynamicColor.collectAsState(initial = false).value
            val reducedMotion = settings.reducedMotion.collectAsState(initial = false).value
            val themeModeName = settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM.name).value
            YounekoRateTheme(dynamicColor = dynamicColor, reducedMotion = reducedMotion, themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)) {
                YounekoNavHost()
            }
        }
    }

    companion object {
        private const val KEY_NOTIFICATIONS_DECLINED = "notifications_declined"
    }
}
