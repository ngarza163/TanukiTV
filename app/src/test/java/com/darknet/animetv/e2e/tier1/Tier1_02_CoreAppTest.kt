package com.darknet.animetv.e2e.tier1

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.AnimeTvApplication
import com.darknet.animetv.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = AnimeTvApplication::class)
class Tier1_02_CoreAppTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<AnimeTvApplication>()
    }

    private fun readManifestContent(): String {
        val path1 = File("src/main/AndroidManifest.xml")
        if (path1.exists()) return path1.readText()
        val path2 = File("app/src/main/AndroidManifest.xml")
        if (path2.exists()) return path2.readText()
        return ""
    }

    @Test
    fun testApplicationSingletonInitialized() {
        val app = AnimeTvApplication.getInstance()
        assertNotNull("AnimeTvApplication instance should not be null", app)
        assertNotNull("Application storage should be initialized", AnimeTvApplication.appStorage)
    }

    @Test
    fun testLeanbackLauncherIntentResolvesToMainActivity() {
        val manifestXml = readManifestContent()
        assertFalse("Manifest must be readable", manifestXml.isBlank())
        assertTrue("Manifest must declare LEANBACK_LAUNCHER", manifestXml.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue("Manifest must declare MainActivity", manifestXml.contains(".MainActivity"))
    }

    @Test
    fun testMainActivityClassDefinition() {
        val activityClass = MainActivity::class.java
        assertNotNull("MainActivity class must exist", activityClass)
        assertTrue("MainActivity must extend ComponentActivity", ComponentActivity::class.java.isAssignableFrom(activityClass))
    }

    @Test
    fun testTvBannerOrIconConfiguredInManifest() {
        val manifestXml = readManifestContent()
        assertFalse("Manifest must be readable", manifestXml.isBlank())
        assertTrue("Manifest must declare TV banner or icon", manifestXml.contains("android:banner") || manifestXml.contains("android:icon"))
        assertTrue("Manifest must declare leanback software feature", manifestXml.contains("android.software.leanback"))
        assertTrue("Manifest must declare touchscreen false", manifestXml.contains("android.hardware.touchscreen"))
    }

    @Test
    fun testApplicationStorageAccessorAcrossThreads() {
        val storage1 = AnimeTvApplication.appStorage
        val thread = Thread {
            val storage2 = AnimeTvApplication.appStorage
            assertSame("Storage singleton must be identical across threads", storage1, storage2)
        }
        thread.start()
        thread.join()
    }
}
