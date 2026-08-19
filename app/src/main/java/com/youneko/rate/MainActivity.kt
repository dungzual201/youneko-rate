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
import javax.inject.Inject
import com.youneko.rate.navigation.YounekoNavHost
import com.youneko.rate.ui.YounekoRateTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var mediaScanCoordinator: MediaScanCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaScanCoordinator.attach(this)
        setContent {
            val settings = remember { SettingsDataStore(applicationContext) }
            val dynamicColor by settings.dynamicColor.collectAsState(initial = false)
            YounekoRateTheme(dynamicColor = dynamicColor) {
                YounekoNavHost()
            }
        }
    }
}
