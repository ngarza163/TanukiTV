package com.darknet.animetv.e2e.harness

enum class TvDpadKey {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    BACK,
    MEDIA_PLAY_PAUSE,
    MEDIA_FAST_FORWARD,
    MEDIA_REWIND
}

data class TvFocusItem(
    val id: String,
    val rowIndex: Int,
    val colIndex: Int,
    val isFocused: Boolean = false,
    val scaleFactor: Float = if (isFocused) 1.08f else 1.0f,
    val hasGlowBorder: Boolean = isFocused
)

class TvGridNavigator(
    val numRows: Int,
    val numCols: Int,
    initialRow: Int = 0,
    initialCol: Int = 0
) {
    var currentRow: Int = initialRow
        private set
    var currentCol: Int = initialCol
        private set

    val currentItemIndex: Int
        get() = currentRow * numCols + currentCol

    fun handleKeyEvent(key: TvDpadKey): Boolean {
        return when (key) {
            TvDpadKey.DPAD_UP -> {
                if (currentRow > 0) {
                    currentRow--
                    true
                } else false
            }
            TvDpadKey.DPAD_DOWN -> {
                if (currentRow < numRows - 1) {
                    currentRow++
                    true
                } else false
            }
            TvDpadKey.DPAD_LEFT -> {
                if (currentCol > 0) {
                    currentCol--
                    true
                } else false
            }
            TvDpadKey.DPAD_RIGHT -> {
                if (currentCol < numCols - 1) {
                    currentCol++
                    true
                } else false
            }
            TvDpadKey.DPAD_CENTER -> true
            else -> false
        }
    }

    fun getFocusItem(idPrefix: String = "item"): TvFocusItem {
        return TvFocusItem(
            id = "${idPrefix}_r${currentRow}_c${currentCol}",
            rowIndex = currentRow,
            colIndex = currentCol,
            isFocused = true
        )
    }
}

class TvPlayerHudSimulator(
    val autoHideTimeoutMs: Long = 5000L,
    val seekStepMs: Long = 10_000L
) {
    var isHudVisible: Boolean = true
        private set
    var lastInteractionTimestamp: Long = System.currentTimeMillis()
        private set
    var isPlaying: Boolean = true
        private set
    var currentPositionMs: Long = 0L
        private set
    var durationMs: Long = 1_440_000L // 24 minutes
        private set
    var seekPillVisible: Boolean = false
        private set
    var seekPillOffset: Long = 0L
        private set

    fun onUserInteraction(currentTimeMs: Long = System.currentTimeMillis()) {
        isHudVisible = true
        lastInteractionTimestamp = currentTimeMs
    }

    fun onTimeTick(currentTimeMs: Long) {
        if (isHudVisible && (currentTimeMs - lastInteractionTimestamp >= autoHideTimeoutMs)) {
            isHudVisible = false
            seekPillVisible = false
        }
    }

    fun onDpadKey(key: TvDpadKey, currentTimeMs: Long = System.currentTimeMillis()) {
        onUserInteraction(currentTimeMs)
        when (key) {
            TvDpadKey.MEDIA_PLAY_PAUSE, TvDpadKey.DPAD_CENTER -> {
                isPlaying = !isPlaying
            }
            TvDpadKey.DPAD_RIGHT, TvDpadKey.MEDIA_FAST_FORWARD -> {
                seekForward()
            }
            TvDpadKey.DPAD_LEFT, TvDpadKey.MEDIA_REWIND -> {
                seekRewind()
            }
            TvDpadKey.BACK -> {
                if (isHudVisible) {
                    isHudVisible = false
                    seekPillVisible = false
                }
            }
            else -> {}
        }
    }

    fun seekForward() {
        val target = currentPositionMs + seekStepMs
        currentPositionMs = target.coerceAtMost(durationMs)
        seekPillVisible = true
        seekPillOffset = +10L
    }

    fun seekRewind() {
        val target = currentPositionMs - seekStepMs
        currentPositionMs = target.coerceAtLeast(0L)
        seekPillVisible = true
        seekPillOffset = -10L
    }

    fun setPosition(positionMs: Long, totalDurationMs: Long = durationMs) {
        currentPositionMs = positionMs.coerceIn(0L, totalDurationMs)
        durationMs = totalDurationMs
    }

    val progressRatio: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
