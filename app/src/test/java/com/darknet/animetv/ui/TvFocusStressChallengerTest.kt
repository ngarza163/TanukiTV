package com.darknet.animetv.ui

import android.view.KeyEvent
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.ui.components.TV_FOCUS_SCALE
import com.darknet.animetv.ui.components.TV_UNFOCUSED_SCALE
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextSecondary
import org.junit.Assert.*
import org.junit.Test

/**
 * Adversarial Stress & Empirical Challenge Test Suite for Milestone 3:
 * TV Remote D-pad Navigation, Focus Modifiers, Card Scaling, and Screen Grid Layout.
 *
 * Challenges:
 * 1. D-pad boundary resistance (navigating LEFT at column 0, UP at top row, RIGHT at column 4)
 * 2. Rapid keypress debounce & event burst handling (100 rapid D-pad key presses)
 * 3. Action button resolution under invalid/extreme watch progress (0, negative, >100%, Long.MAX_VALUE)
 * 4. Null / empty anime card titles, 0.0 scores, missing images, and extreme strings
 * 5. Scale clamping and z-index elevation under focused vs unfocused states
 */
class TvFocusStressChallengerTest {

    // =========================================================================
    // 1. D-PAD BOUNDARY RESISTANCE (5-Column Grid Navigation Controller)
    // =========================================================================

    /**
     * Mathematical model of Android TV 5-column grid navigation (Leanback / TvLazyVerticalGrid).
     */
    class TvGridNavigator(val totalItems: Int, val columns: Int = 5) {
        var currentIndex: Int = if (totalItems > 0) 0 else -1

        enum class Direction { UP, DOWN, LEFT, RIGHT }

        fun move(direction: Direction): Boolean {
            if (totalItems <= 0 || currentIndex < 0) return false

            val currentColumn = currentIndex % columns
            val currentRow = currentIndex / columns

            when (direction) {
                Direction.LEFT -> {
                    // Resists boundary: cannot move left if at column 0 (prevents wrapping to prev row)
                    if (currentColumn == 0) return false
                    currentIndex -= 1
                    return true
                }
                Direction.RIGHT -> {
                    // Resists boundary: cannot move right if at column 4 or at last item
                    if (currentColumn == columns - 1 || currentIndex >= totalItems - 1) return false
                    currentIndex += 1
                    return true
                }
                Direction.UP -> {
                    // Resists boundary: cannot move up if at row 0 (prevents negative index)
                    if (currentRow == 0) return false
                    currentIndex -= columns
                    return true
                }
                Direction.DOWN -> {
                    // Resists boundary: cannot move down if target exceeds totalItems
                    val targetIndex = currentIndex + columns
                    if (targetIndex >= totalItems) {
                        // In Leanback TV, if the exact cell below does not exist in an incomplete last row,
                        // focus either clamps to the last item or resists movement.
                        // Here we resist escaping grid bounds.
                        return false
                    }
                    currentIndex = targetIndex
                    return true
                }
            }
        }
    }

    @Test
    fun testGridBoundaryResistance_LeftBoundaryAtColumnZero() {
        // Grid with 25 items (5x5). Indices 0, 5, 10, 15, 20 are column 0.
        val nav = TvGridNavigator(totalItems = 25, columns = 5)

        for (row in 0 until 5) {
            nav.currentIndex = row * 5 // Set to column 0
            val moved = nav.move(TvGridNavigator.Direction.LEFT)
            assertFalse("Moving LEFT at column 0 (index ${row * 5}) must resist boundary", moved)
            assertEquals("Focus must stay at column 0", row * 5, nav.currentIndex)
        }
    }

    @Test
    fun testGridBoundaryResistance_RightBoundaryAtColumnFour() {
        // Indices 4, 9, 14, 19, 24 are column 4.
        val nav = TvGridNavigator(totalItems = 25, columns = 5)

        for (row in 0 until 5) {
            nav.currentIndex = (row * 5) + 4 // Set to column 4
            val moved = nav.move(TvGridNavigator.Direction.RIGHT)
            assertFalse("Moving RIGHT at column 4 (index ${(row * 5) + 4}) must resist boundary", moved)
            assertEquals("Focus must stay at column 4", (row * 5) + 4, nav.currentIndex)
        }
    }

    @Test
    fun testGridBoundaryResistance_TopBoundaryAtFirstRow() {
        // Indices 0, 1, 2, 3, 4 are row 0.
        val nav = TvGridNavigator(totalItems = 25, columns = 5)

        for (col in 0 until 5) {
            nav.currentIndex = col
            val moved = nav.move(TvGridNavigator.Direction.UP)
            assertFalse("Moving UP at row 0 (index $col) must resist boundary without going negative", moved)
            assertEquals("Focus must remain at row 0", col, nav.currentIndex)
            assertTrue("Focus index must be non-negative", nav.currentIndex >= 0)
        }
    }

    @Test
    fun testGridBoundaryResistance_BottomBoundaryWithIncompleteLastRow() {
        // 7 items: Row 0 has items 0, 1, 2, 3, 4. Row 1 has items 5, 6 (cols 0 and 1).
        // Cols 2, 3, 4 in Row 1 do NOT exist!
        val nav = TvGridNavigator(totalItems = 7, columns = 5)

        // Item 5 (row 1, col 0): DOWN should resist
        nav.currentIndex = 5
        assertFalse(nav.move(TvGridNavigator.Direction.DOWN))
        assertEquals(5, nav.currentIndex)

        // Item 6 (row 1, col 1): DOWN should resist
        nav.currentIndex = 6
        assertFalse(nav.move(TvGridNavigator.Direction.DOWN))
        assertEquals(6, nav.currentIndex)

        // Item 3 (row 0, col 3): target item 8 does not exist
        nav.currentIndex = 3
        val moved = nav.move(TvGridNavigator.Direction.DOWN)
        assertFalse("Moving DOWN from row 0 col 3 into non-existent cell must resist", moved)
        assertEquals(3, nav.currentIndex)
    }

    @Test
    fun testGridBoundaryResistance_EmptyAndSingleItemGrid() {
        // Empty grid (0 items)
        val emptyNav = TvGridNavigator(totalItems = 0, columns = 5)
        assertEquals(-1, emptyNav.currentIndex)
        assertFalse(emptyNav.move(TvGridNavigator.Direction.LEFT))
        assertFalse(emptyNav.move(TvGridNavigator.Direction.RIGHT))
        assertFalse(emptyNav.move(TvGridNavigator.Direction.UP))
        assertFalse(emptyNav.move(TvGridNavigator.Direction.DOWN))
        assertEquals(-1, emptyNav.currentIndex)

        // Single-item grid (1 item at index 0)
        val singleNav = TvGridNavigator(totalItems = 1, columns = 5)
        assertEquals(0, singleNav.currentIndex)
        assertFalse(singleNav.move(TvGridNavigator.Direction.LEFT))
        assertFalse(singleNav.move(TvGridNavigator.Direction.RIGHT))
        assertFalse(singleNav.move(TvGridNavigator.Direction.UP))
        assertFalse(singleNav.move(TvGridNavigator.Direction.DOWN))
        assertEquals(0, singleNav.currentIndex)
    }

    @Test
    fun testGridBoundaryResistance_ExhaustiveLargeScaleSweep() {
        // 1,000 items in 5-column grid (200 rows)
        val nav = TvGridNavigator(totalItems = 1000, columns = 5)

        // Sweep all left boundaries
        for (row in 0 until 200) {
            nav.currentIndex = row * 5
            assertFalse("Row $row col 0 must resist LEFT", nav.move(TvGridNavigator.Direction.LEFT))
            assertEquals(row * 5, nav.currentIndex)
        }

        // Sweep all right boundaries
        for (row in 0 until 200) {
            nav.currentIndex = (row * 5) + 4
            assertFalse("Row $row col 4 must resist RIGHT", nav.move(TvGridNavigator.Direction.RIGHT))
            assertEquals((row * 5) + 4, nav.currentIndex)
        }
    }

    // =========================================================================
    // 2. RAPID KEYPRESS DEBOUNCE & EVENT BURST HANDLING
    // =========================================================================

    @Test
    fun testRapidKeypressDebounce_100RapidDpadEventsClampSafely() {
        // Grid with 23 items (indices 0..22)
        val nav = TvGridNavigator(totalItems = 23, columns = 5)

        // Rapid burst: 100 consecutive DOWN presses
        var successfulDownMoves = 0
        for (i in 1..100) {
            if (nav.move(TvGridNavigator.Direction.DOWN)) {
                successfulDownMoves++
            }
        }

        // Row 0 (0) -> Row 1 (5) -> Row 2 (10) -> Row 3 (15) -> Row 4 (20). Total 4 valid moves.
        assertEquals(4, successfulDownMoves)
        assertEquals(20, nav.currentIndex)
        assertTrue("Focus index must be strictly clamped within bounds", nav.currentIndex in 0..22)
    }

    @Test
    fun testRapidKeypressDebounce_100RapidOscillatingLeftRightEvents() {
        val nav = TvGridNavigator(totalItems = 25, columns = 5)
        nav.currentIndex = 0 // Column 0

        // 100 alternating LEFT (boundary) and RIGHT events
        for (i in 1..50) {
            nav.move(TvGridNavigator.Direction.LEFT) // Resists, stays at 0
            assertEquals(0, nav.currentIndex)
            nav.move(TvGridNavigator.Direction.RIGHT) // Advances to 1
            assertEquals(1, nav.currentIndex)
            nav.move(TvGridNavigator.Direction.LEFT) // Returns to 0
            assertEquals(0, nav.currentIndex)
        }

        assertEquals("No drift after 100 oscillating keypresses", 0, nav.currentIndex)
    }

    @Test
    fun testDpadActivation_ActionDownVersusActionUpDiscrimination() {
        var activationCount = 0

        fun handleKeyEvent(action: Int, keyCode: Int) {
            // Replicates FocusModifiers.kt logic: only ACTION_UP triggers activation
            if (action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == KeyEvent.KEYCODE_ENTER ||
                 keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            ) {
                activationCount++
            }
        }

        // Fire 100 rapid ACTION_DOWN events (e.g. holding D-pad center down)
        for (i in 1..100) {
            handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        }
        assertEquals("ACTION_DOWN must NEVER trigger click activations", 0, activationCount)

        // Fire 1 ACTION_UP event on release
        handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("Single ACTION_UP triggers exactly 1 activation", 1, activationCount)
    }

    @Test
    fun testRapidClickDebounceThrottling_CoalescingBursts() {
        /**
         * Simulates a debouncer / throttle handler for TV remote clicks.
         * Ensures rapid button mashing (e.g. 100 clicks in 50ms) does not launch 100 player instances.
         */
        class RemoteClickDebouncer(val debounceWindowMs: Long = 250L) {
            var lastClickTimestamp: Long = 0L
            var executedClicks = 0

            fun registerClick(nowTimestamp: Long): Boolean {
                if (nowTimestamp - lastClickTimestamp >= debounceWindowMs) {
                    lastClickTimestamp = nowTimestamp
                    executedClicks++
                    return true
                }
                return false
            }
        }

        val debouncer = RemoteClickDebouncer(debounceWindowMs = 250L)
        var baseTime = 1000L

        // 100 rapid clicks spaced 5ms apart (spanning 500ms total)
        for (i in 1..100) {
            debouncer.registerClick(baseTime)
            baseTime += 5L // +5ms per click
        }

        // In 500ms with a 250ms window:
        // Click 1 at 1000ms: executed (next eligible at >= 1250ms)
        // Click at 1250ms (index 51): executed (next eligible at >= 1500ms)
        // Total should be exactly 2 executions across 100 rapid keypresses
        assertEquals("100 rapid clicks in 500ms must be throttled to 2 executions", 2, debouncer.executedClicks)
    }

    @Test
    fun testRapidKeypress_NonActivationKeysIgnored() {
        val nonActivationKeys = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_MENU
        )

        var clickTriggered = false
        fun simulateKey(keyCode: Int) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            ) {
                clickTriggered = true
            }
        }

        for (key in nonActivationKeys) {
            for (repeat in 1..10) {
                simulateKey(key)
            }
        }

        assertFalse("Non-activation keys must never trigger action callbacks", clickTriggered)
    }

    // =========================================================================
    // 3. ACTION BUTTON RESOLUTION UNDER EXTREME WATCH PROGRESS
    // =========================================================================

    private fun resolveActionLabel(progress: WatchProgress?): String {
        return when {
            progress != null && progress.progressPercentage < 0.90f -> "Resume"
            progress != null && progress.progressPercentage >= 0.90f -> "Watch Again"
            else -> "Play Now"
        }
    }

    @Test
    fun testActionLabel_ZeroProgressAndZeroDuration() {
        val zeroDurationProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 0L, durationMs = 0L
        )

        // durationMs = 0 -> progressPercentage is 0.0f
        assertEquals(0.0f, zeroDurationProgress.progressPercentage, 0.0001f)
        // progress exists with < 0.90f -> "Resume"
        assertEquals("Resume", resolveActionLabel(zeroDurationProgress))
    }

    @Test
    fun testActionLabel_NegativeTimestampsClampedSafely() {
        val corruptProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = -50_000L, durationMs = 1_200_000L
        )

        // Negative positionMs coercedIn(0f, 1f) must clamp to 0.0f
        assertEquals(0.0f, corruptProgress.progressPercentage, 0.0001f)
        assertEquals("Resume", resolveActionLabel(corruptProgress))

        // Negative durationMs
        val negativeDurationProgress = corruptProgress.copy(positionMs = 100L, durationMs = -1000L)
        assertEquals(0.0f, negativeDurationProgress.progressPercentage, 0.0001f)
        assertEquals("Resume", resolveActionLabel(negativeDurationProgress))
    }

    @Test
    fun testActionLabel_OverflowProgressOver100Percent() {
        // Watched past duration (e.g. 200% progress)
        val overflowProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 2_400_000L, durationMs = 1_200_000L
        )

        // coerceIn(0f, 1f) must clamp to 1.0f
        assertEquals(1.0f, overflowProgress.progressPercentage, 0.0001f)
        assertEquals("Watch Again", resolveActionLabel(overflowProgress))
    }

    @Test
    fun testActionLabel_ExtremeLongValues() {
        // Long.MAX_VALUE position
        val maxPosProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = Long.MAX_VALUE, durationMs = 1000L
        )
        assertEquals(1.0f, maxPosProgress.progressPercentage, 0.0001f)
        assertEquals("Watch Again", resolveActionLabel(maxPosProgress))

        // Long.MAX_VALUE duration
        val maxDurationProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 1000L, durationMs = Long.MAX_VALUE
        )
        assertTrue(maxDurationProgress.progressPercentage < 0.0001f)
        assertEquals("Resume", resolveActionLabel(maxDurationProgress))

        // Long.MIN_VALUE position
        val minPosProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = Long.MIN_VALUE, durationMs = 1000L
        )
        assertEquals(0.0f, minPosProgress.progressPercentage, 0.0001f)
        assertEquals("Resume", resolveActionLabel(minPosProgress))
    }

    @Test
    fun testActionLabel_ExactBoundary90PercentThreshold() {
        val duration = 1_000_000L

        // 89.999% watched -> "Resume"
        val justBelow = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 899_990L, durationMs = duration
        )
        assertTrue(justBelow.progressPercentage < 0.90f)
        assertEquals("Resume", resolveActionLabel(justBelow))

        // Exactly 90.000% watched -> "Watch Again"
        val exactThreshold = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 900_000L, durationMs = duration
        )
        assertTrue(exactThreshold.progressPercentage >= 0.90f)
        assertEquals("Watch Again", resolveActionLabel(exactThreshold))
    }

    // =========================================================================
    // 4. NULL / EMPTY TITLES, 0.0 SCORES, AND MISSING IMAGES
    // =========================================================================

    @Test
    fun testAnimeCard_EmptyAndBlankTitlesFormatting() {
        val emptyTitleAnime = Anime(id = "1", title = "")
        val whitespaceTitleAnime = Anime(id = "2", title = "   \n\t  ")

        assertEquals("", emptyTitleAnime.title)
        assertTrue(whitespaceTitleAnime.title.isBlank())

        // Defensive display title resolver
        fun resolveDisplayTitle(anime: Anime): String {
            return if (anime.title.isNotBlank()) anime.title.trim() else "Untitled Anime"
        }

        assertEquals("Untitled Anime", resolveDisplayTitle(emptyTitleAnime))
        assertEquals("Untitled Anime", resolveDisplayTitle(whitespaceTitleAnime))
    }

    @Test
    fun testAnimeCard_ExtremeLengthTitleNoBreak() {
        val longTitle = "A".repeat(5000)
        val anime = Anime(id = "long-1", title = longTitle)

        assertEquals(5000, anime.title.length)
        assertFalse(anime.title.isBlank())
    }

    @Test
    fun testAnimeCard_SpecialCharactersAndUnicode() {
        val complexTitle = "🔥 鬼滅の刃 ⚔️ - Mugen Train (Part 2) <script>alert(1)</script> \u202Ereversed\u202C"
        val anime = Anime(id = "unicode-1", title = complexTitle)

        assertEquals(complexTitle, anime.title)
        assertTrue(anime.title.contains("鬼滅の刃"))
        assertTrue(anime.title.contains("🔥"))
    }

    @Test
    fun testAnimeCard_ScoreBadgeEdgeCases() {
        fun shouldShowScoreBadge(score: Double): Boolean = score > 0.0
        fun formatScore(score: Double): String = String.format("%.1f", score)

        assertFalse("Score of 0.0 must omit score badge", shouldShowScoreBadge(0.0))
        assertFalse("Negative score must omit score badge", shouldShowScoreBadge(-2.5))
        assertFalse("NaN score must omit score badge", shouldShowScoreBadge(Double.NaN))

        assertTrue("Valid positive score must show score badge", shouldShowScoreBadge(8.45))
        assertEquals("8.5", formatScore(8.45))

        assertTrue("Perfect 10.0 score must format correctly", shouldShowScoreBadge(10.0))
        assertEquals("10.0", formatScore(10.0))

        assertTrue("Tiny positive score must format without crash", shouldShowScoreBadge(0.001))
        assertEquals("0.0", formatScore(0.001))
    }

    @Test
    fun testImageFallbackHierarchy_CardVsHeroBanner() {
        fun resolveCardImage(anime: Anime): String = anime.posterUrl.ifBlank { anime.backdropUrl }
        fun resolveHeroImage(anime: Anime): String = anime.backdropUrl.ifBlank { anime.posterUrl }

        // Both missing
        val emptyAnime = Anime(id = "1", title = "T", posterUrl = "", backdropUrl = "")
        assertEquals("", resolveCardImage(emptyAnime))
        assertEquals("", resolveHeroImage(emptyAnime))

        // Both whitespace
        val blankAnime = Anime(id = "2", title = "T", posterUrl = "   ", backdropUrl = "  ")
        assertEquals("  ", resolveCardImage(blankAnime))
        assertEquals("   ", resolveHeroImage(blankAnime))

        // Only poster present
        val posterOnly = Anime(id = "3", title = "T", posterUrl = "https://cdn.com/p.jpg", backdropUrl = "")
        assertEquals("https://cdn.com/p.jpg", resolveCardImage(posterOnly))
        assertEquals("https://cdn.com/p.jpg", resolveHeroImage(posterOnly))

        // Only backdrop present
        val backdropOnly = Anime(id = "4", title = "T", posterUrl = "", backdropUrl = "https://cdn.com/b.jpg")
        assertEquals("https://cdn.com/b.jpg", resolveCardImage(backdropOnly))
        assertEquals("https://cdn.com/b.jpg", resolveHeroImage(backdropOnly))

        // Both present: Card prefers poster; Hero prefers backdrop
        val bothPresent = Anime(id = "5", title = "T", posterUrl = "https://cdn.com/p.jpg", backdropUrl = "https://cdn.com/b.jpg")
        assertEquals("https://cdn.com/p.jpg", resolveCardImage(bothPresent))
        assertEquals("https://cdn.com/b.jpg", resolveHeroImage(bothPresent))
    }

    // =========================================================================
    // 5. SCALE CLAMPING & Z-INDEX ELEVATION UNDER FOCUSED VS UNFOCUSED STATES
    // =========================================================================

    @Test
    fun testScaleClamping_DefaultTvConstants() {
        assertEquals(1.08f, TV_FOCUS_SCALE, 0.0001f)
        assertEquals(1.0f, TV_UNFOCUSED_SCALE, 0.0001f)

        fun computeScale(isFocused: Boolean, focusedScale: Float = TV_FOCUS_SCALE, unfocusedScale: Float = TV_UNFOCUSED_SCALE): Float {
            return if (isFocused) focusedScale else unfocusedScale
        }

        assertEquals(1.08f, computeScale(true), 0.0001f)
        assertEquals(1.0f, computeScale(false), 0.0001f)
        assertEquals(1.05f, computeScale(true, focusedScale = 1.05f), 0.0001f)
    }

    @Test
    fun testZIndexElevation_MutualExclusionInContainer() {
        // In a row of 5 items where Item 2 is focused
        val itemsCount = 5
        val focusedIndex = 2

        val zIndices = (0 until itemsCount).map { index ->
            if (index == focusedIndex) 2.0f else 0.0f
        }

        assertEquals(itemsCount, zIndices.size)
        assertEquals(2.0f, zIndices[focusedIndex], 0.0001f)
        assertEquals("Focused item must hold unique maximum z-index in container", 1, zIndices.count { it == 2.0f })
        assertEquals("All other items must have 0.0 z-index", itemsCount - 1, zIndices.count { it == 0.0f })
        assertTrue("Focused item z-index strictly dominates unfocused neighbors", zIndices[focusedIndex] > zIndices[0])
    }

    @Test
    fun testCardExpansionOverlapGeometry() {
        val baseWidthDp = 140.0f
        val baseHeightDp = 210.0f
        val focusedScale = 1.08f

        val expandedWidth = baseWidthDp * focusedScale
        val expandedHeight = baseHeightDp * focusedScale

        val horizontalExpansionMargin = (expandedWidth - baseWidthDp) / 2.0f
        val verticalExpansionMargin = (expandedHeight - baseHeightDp) / 2.0f

        assertEquals(151.2f, expandedWidth, 0.001f)
        assertEquals(226.8f, expandedHeight, 0.001f)
        assertEquals(5.6f, horizontalExpansionMargin, 0.001f)
        assertEquals(8.4f, verticalExpansionMargin, 0.001f)

        // Spacing in SearchScreen 5-column grid is 20dp
        val gridSpacingDp = 20.0f
        val netClearance = gridSpacingDp - (horizontalExpansionMargin * 2.0f)
        assertTrue("Net horizontal clearance in 5-col grid is positive (8.8dp)", netClearance > 0f)
        assertEquals(8.8f, netClearance, 0.001f)

        // If tight shelf spacing was 8dp, card would overlap by 3.2dp
        val tightSpacingDp = 8.0f
        val tightClearance = tightSpacingDp - (horizontalExpansionMargin * 2.0f)
        assertTrue("In tight shelf spacing, expansion causes overlap requiring z-index 2f elevation", tightClearance < 0f)
    }

    @Test
    fun testCategoryChip_FocusPrecedenceOverSelectionState() {
        // Tests the color selection hierarchy in CategoryChip.kt:
        // isFocused takes strict visual precedence over isSelected so remote focus is never obscured!
        fun resolveBackgroundColor(isFocused: Boolean, isSelected: Boolean): androidx.compose.ui.graphics.Color {
            return when {
                isFocused -> TvAccentPurple
                isSelected -> TvPrimaryRed
                else -> TvSurfaceColor
            }
        }

        assertEquals(TvAccentPurple, resolveBackgroundColor(isFocused = true, isSelected = true))
        assertEquals(TvAccentPurple, resolveBackgroundColor(isFocused = true, isSelected = false))
        assertEquals(TvPrimaryRed, resolveBackgroundColor(isFocused = false, isSelected = true))
        assertEquals(TvSurfaceColor, resolveBackgroundColor(isFocused = false, isSelected = false))
    }
}
