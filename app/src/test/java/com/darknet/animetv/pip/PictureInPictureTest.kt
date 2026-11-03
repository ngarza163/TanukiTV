package com.darknet.animetv.pip

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PictureInPictureTest {

    @Test
    fun testPictureInPicture16by9AspectRatioParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rational = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()

            assertNotNull(params)
            assertEquals(16, rational.numerator)
            assertEquals(9, rational.denominator)
        }
    }
}
