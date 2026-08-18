package com.youneko.rate

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = YounekoRateApplication::class, sdk = [35])
class NetworkManifestTest {
    @Test
    fun mergedManifestContainsRequiredNetworkPermissions() {
        val context = ApplicationProvider.getApplicationContext<YounekoRateApplication>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(permissions.contains(Manifest.permission.INTERNET))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE))
    }
}
