package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.TvDpadKey
import com.darknet.animetv.e2e.harness.TvFocusItem
import com.darknet.animetv.e2e.harness.TvGridNavigator
import org.junit.Assert.*
import org.junit.Test

class Tier1_10_RemoteFocusTest {

    @Test
    fun testFocusScaleFactorAmplification() {
        val unfocused = TvFocusItem(id = "card-1", rowIndex = 0, colIndex = 0, isFocused = false)
        assertEquals(1.0f, unfocused.scaleFactor, 0.001f)
        assertFalse(unfocused.hasGlowBorder)

        val focused = TvFocusItem(id = "card-1", rowIndex = 0, colIndex = 0, isFocused = true)
        assertEquals(1.08f, focused.scaleFactor, 0.001f)
        assertTrue(focused.hasGlowBorder)
    }

    @Test
    fun testDpadDirectionalNavigation() {
        val navigator = TvGridNavigator(numRows = 3, numCols = 3, initialRow = 0, initialCol = 0)
        assertEquals(0, navigator.currentRow)
        assertEquals(0, navigator.currentCol)

        val movedRight = navigator.handleKeyEvent(TvDpadKey.DPAD_RIGHT)
        assertTrue(movedRight)
        assertEquals(1, navigator.currentCol)

        val movedDown = navigator.handleKeyEvent(TvDpadKey.DPAD_DOWN)
        assertTrue(movedDown)
        assertEquals(1, navigator.currentRow)

        assertEquals(4, navigator.currentItemIndex) // row 1 * 3 + col 1 = 4
    }

    @Test
    fun testDpadBoundaryResistance() {
        val navigator = TvGridNavigator(numRows = 2, numCols = 2, initialRow = 0, initialCol = 0)

        // Try moving UP beyond top edge
        val movedUp = navigator.handleKeyEvent(TvDpadKey.DPAD_UP)
        assertFalse("Should not navigate past top row", movedUp)
        assertEquals(0, navigator.currentRow)

        // Try moving LEFT beyond left edge
        val movedLeft = navigator.handleKeyEvent(TvDpadKey.DPAD_LEFT)
        assertFalse("Should not navigate past left col", movedLeft)
        assertEquals(0, navigator.currentCol)
    }

    @Test
    fun testDpadCenterKeyActivatesItem() {
        val navigator = TvGridNavigator(numRows = 2, numCols = 2)
        val handled = navigator.handleKeyEvent(TvDpadKey.DPAD_CENTER)
        assertTrue("DPAD_CENTER should be handled as item activation", handled)
    }

    @Test
    fun testFocusItemUniqueIdGeneration() {
        val navigator = TvGridNavigator(numRows = 4, numCols = 5)
        navigator.handleKeyEvent(TvDpadKey.DPAD_DOWN)
        navigator.handleKeyEvent(TvDpadKey.DPAD_RIGHT)

        val item = navigator.getFocusItem("shelf_anime")
        assertEquals("shelf_anime_r1_c1", item.id)
        assertTrue(item.isFocused)
    }
}
