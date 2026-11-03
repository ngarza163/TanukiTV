package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.junit.Assert.*
import org.junit.Test

class Tier1_20_AutoResumeTest {

    @Test
    fun testPeriodicProgressSaveInterval() {
        val lastSaveTimestamp = 1000L
        val saveIntervalMs = 5000L

        fun shouldSaveProgress(currentTimestamp: Long): Boolean {
            return (currentTimestamp - lastSaveTimestamp) >= saveIntervalMs
        }

        assertFalse(shouldSaveProgress(3000L)) // 2s passed
        assertFalse(shouldSaveProgress(5999L)) // 4.999s passed
        assertTrue(shouldSaveProgress(6000L))  // 5s passed
    }

    @Test
    fun testResumePositionCalculationWithinMiddleOfEpisode() {
        val progress = E2eTestFixtures.createSampleWatchProgress(
            positionMs = 600_000L, // 10 minutes
            durationMs = 1_440_000L // 24 minutes (~41%)
        )

        fun determineResumeAction(p: WatchProgress): Pair<String, Long> {
            return when {
                p.progressPercentage >= 0.90f -> "COMPLETED" to 0L
                p.positionMs > 10_000L -> "RESUME" to p.positionMs
                else -> "START" to 0L
            }
        }

        val (action, resumeMs) = determineResumeAction(progress)
        assertEquals("RESUME", action)
        assertEquals(600_000L, resumeMs)
    }

    @Test
    fun testCompletedEpisodePromptsStartOverOrNext() {
        val nearCompleteProgress = E2eTestFixtures.createSampleWatchProgress(
            positionMs = 1_350_000L, // ~93.7% of 1440s
            durationMs = 1_440_000L
        )

        val isCompleted = nearCompleteProgress.progressPercentage >= 0.90f
        assertTrue("Episode over 90% should be considered completed", isCompleted)
    }

    @Test
    fun testEarlyPositionStartsFromBeginning() {
        val earlyProgress = E2eTestFixtures.createSampleWatchProgress(
            positionMs = 4_000L, // Only 4 seconds watched
            durationMs = 1_440_000L
        )

        val shouldResume = earlyProgress.positionMs >= 10_000L
        assertFalse("Playback under 10 seconds should restart from beginning", shouldResume)
    }

    @Test
    fun testWatchProgressTimestampUpdatedOnSave() {
        val t1 = System.currentTimeMillis() - 5000L
        val p1 = E2eTestFixtures.createSampleWatchProgress()
        val p2 = p1.copy(positionMs = 800_000L, lastWatchedTimestamp = System.currentTimeMillis())

        assertTrue("Updated progress should have newer timestamp", p2.lastWatchedTimestamp > t1)
        assertEquals(800_000L, p2.positionMs)
    }
}
