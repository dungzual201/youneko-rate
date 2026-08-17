package com.youneko.rate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.youneko.rate.ui.YounekoRateTheme
import com.youneko.rate.navigation.YounekoNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YounekoRateTheme {
                YounekoNavHost()
            }
        }
    }
}
