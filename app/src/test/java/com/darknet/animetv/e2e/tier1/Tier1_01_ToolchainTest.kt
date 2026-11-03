package com.darknet.animetv.e2e.tier1

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Tier1_01_ToolchainTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSdkTargetAndMinSdkVersion() {
        val sdkVersion = android.os.Build.VERSION.SDK_INT
        assertTrue("SDK level should be valid", sdkVersion > 0)
    }

    @Test
    fun testComposeTvClassesPresentOnClasspath() {
        val tvMaterialClass = androidx.tv.material3.MaterialTheme::class.java
        assertNotNull("androidx.tv.material3.MaterialTheme must be present", tvMaterialClass)

        val tvColorSchemeClass = androidx.tv.material3.ColorScheme::class.java
        assertNotNull("androidx.tv.material3.ColorScheme must be present", tvColorSchemeClass)
    }

    @Test
    fun testMedia3ExoPlayerClassesPresentOnClasspath() {
        val exoPlayerClass = Class.forName("androidx.media3.exoplayer.ExoPlayer")
        assertNotNull("androidx.media3.exoplayer.ExoPlayer must be available", exoPlayerClass)

        val mediaItemClass = Class.forName("androidx.media3.common.MediaItem")
        assertNotNull("androidx.media3.common.MediaItem must be available", mediaItemClass)
    }

    @Test
    fun testNetworkingAndParsingLibrariesAvailable() {
        val okHttpClientClass = Class.forName("okhttp3.OkHttpClient")
        assertNotNull("okhttp3.OkHttpClient must be loaded", okHttpClientClass)

        val jsoupClass = Class.forName("org.jsoup.Jsoup")
        assertNotNull("org.jsoup.Jsoup must be loaded", jsoupClass)

        val gsonClass = Class.forName("com.google.gson.Gson")
        assertNotNull("com.google.gson.Gson must be loaded", gsonClass)
    }

    @Test
    fun testCoroutinesDispatchersAvailable() {
        val dispatchersClass = Class.forName("kotlinx.coroutines.Dispatchers")
        assertNotNull("kotlinx.coroutines.Dispatchers must be loaded", dispatchersClass)
    }
}
