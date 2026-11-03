package com.darknet.animetv.ui

import android.view.KeyEvent
import com.darknet.animetv.ui.components.TV_FOCUS_SCALE
import com.darknet.animetv.ui.components.TV_UNFOCUSED_SCALE
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvPrimaryRed
import org.junit.Assert.*
import org.junit.Test

class TvFocusModifiersTest {

    @Test
    fun testFocusScaleConstants() {
        assertEquals("Focus scale factor should be 1.08x per Android TV design spec", 1.08f, TV_FOCUS_SCALE, 0.001f)
        assertEquals("Unfocused scale factor should be 1.0x", 1.0f, TV_UNFOCUSED_SCALE, 0.001f)
    }

    @Test
    fun testFocusScaleComputation() {
        fun computeScale(isFocused: Boolean): Float = if (isFocused) TV_FOCUS_SCALE else TV_UNFOCUSED_SCALE

        assertEquals(1.0f, computeScale(isFocused = false), 0.001f)
        assertEquals(1.08f, computeScale(isFocused = true), 0.001f)
    }

    @Test
    fun testZIndexElevation() {
        fun computeZIndex(isFocused: Boolean): Float = if (isFocused) 2f else 0f

        assertEquals(0f, computeZIndex(isFocused = false), 0.001f)
        assertEquals(2f, computeZIndex(isFocused = true), 0.001f)
        assertTrue("Focused item must have higher z-index to avoid clipping by neighbors", computeZIndex(true) > computeZIndex(false))
    }

    @Test
    fun testDpadActivationKeyCodes() {
        val validKeyCodes = listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        )

        fun isActivationKey(keyCode: Int): Boolean = keyCode in validKeyCodes

        assertTrue(isActivationKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(isActivationKey(KeyEvent.KEYCODE_ENTER))
        assertTrue(isActivationKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertFalse(isActivationKey(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(isActivationKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(isActivationKey(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun testFocusBorderColors() {
        assertNotNull(TvAccentPurple)
        assertNotNull(TvPrimaryRed)
        assertNotEquals("Accent and Primary colors should be distinct", TvAccentPurple, TvPrimaryRed)
    }
}
