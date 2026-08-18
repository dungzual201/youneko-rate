package com.youneko.rate

import androidx.test.core.app.ActivityScenario
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = YounekoRateApplication::class, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityThemeLaunchTest {
    @Test
    fun mainActivityLaunchesWithoutAppCompatThemeException() {
        launchAndAssertRunning()
    }

    @Test
    @Config(qualifiers = "night")
    fun mainActivityLaunchesWithoutAppCompatThemeExceptionInNightMode() {
        launchAndAssertRunning()
    }

    @Test
    fun systemLocaleLightLaunches() = launchWithLocale("")

    @Test
    fun englishLocaleLightLaunches() = launchWithLocale("en")

    @Test
    fun vietnameseLocaleLightLaunches() = launchWithLocale("vi")

    @Test
    @Config(qualifiers = "night")
    fun systemLocaleNightLaunches() = launchWithLocale("")

    @Test
    @Config(qualifiers = "night")
    fun englishLocaleNightLaunches() = launchWithLocale("en")

    @Test
    @Config(qualifiers = "night")
    fun vietnameseLocaleNightLaunches() = launchWithLocale("vi")

    private fun launchWithLocale(languageTag: String) {
        try {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            launchAndAssertRunning()
        } finally {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    private fun launchAndAssertRunning() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(!activity.isFinishing) { "MainActivity finished during launch" }
            }
        }
    }
}
