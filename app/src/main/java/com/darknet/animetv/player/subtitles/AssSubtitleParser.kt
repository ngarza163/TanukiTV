package com.darknet.animetv.player.subtitles

import androidx.compose.ui.graphics.Color
import java.util.regex.Pattern

/**
 * Data structures for Advanced SubStation Alpha (.ass / .ssa) styled anime subtitles.
 */
data class AssStyle(
    val name: String,
    val fontName: String = "Sans-Serif",
    val fontSize: Float = 24f,
    val primaryColor: Color = Color.White,
    val outlineColor: Color = Color.Black,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val alignment: Int = 2 // 2 = bottom-center
)

data class AssCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val style: AssStyle,
    val alignment: Int = 2,
    val posX: Float? = null,
    val posY: Float? = null,
    val overrideColor: Color? = null
)

/**
 * High-performance parser for .ass subtitle scripts.
 * Extracts styles, dialogue cues, screen position coordinates {\pos(x,y)}, and text styling.
 */
object AssSubtitleParser {

    private val POS_REGEX = Pattern.compile("""\\pos\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""")
    private val COLOR_REGEX = Pattern.compile("""\\(?:c|1c)&H([0-9a-fA-F]{6})&""")
    private val TAGS_REGEX = Pattern.compile("""\{[^}]*\}""")

    /**
     * Parses a timestamp formatted as "H:MM:SS.CC" or "H:MM:SS.CCC" into milliseconds.
     */
    fun parseTimestamp(timestamp: String): Long {
        val parts = timestamp.trim().split(":")
        if (parts.size < 3) return 0L

        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L

        val secParts = parts[2].split(".")
        val seconds = secParts[0].toLongOrNull() ?: 0L
        val centis = if (secParts.size > 1) {
            val dec = secParts[1].padEnd(3, '0').take(3)
            dec.toLongOrNull() ?: 0L
        } else 0L

        return (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1000L) + centis
    }

    /**
     * Parses an .ass / .ssa content script into a list of styled AssCue items.
     */
    fun parse(assScript: String): List<AssCue> {
        val lines = assScript.lines()
        val styles = mutableMapOf<String, AssStyle>()
        val cues = mutableListOf<AssCue>()

        val defaultStyle = AssStyle(name = "Default")
        styles["Default"] = defaultStyle

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";")) continue

            if (line.startsWith("Style:", ignoreCase = true)) {
                // Style: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, ...
                val values = line.substringAfter(":").split(",").map { it.trim() }
                if (values.size >= 3) {
                    val styleName = values[0]
                    val fontName = values[1]
                    val fontSize = values[2].toFloatOrNull() ?: 24f
                    val bold = values.getOrNull(7) == "1" || values.getOrNull(7) == "-1"
                    val italic = values.getOrNull(8) == "1" || values.getOrNull(8) == "-1"
                    val alignment = values.getOrNull(18)?.toIntOrNull() ?: 2

                    styles[styleName] = AssStyle(
                        name = styleName,
                        fontName = fontName,
                        fontSize = fontSize,
                        isBold = bold,
                        isItalic = italic,
                        alignment = alignment
                    )
                }
            } else if (line.startsWith("Dialogue:", ignoreCase = true)) {
                // Dialogue: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                val parts = line.substringAfter(":").split(",", limit = 10)
                if (parts.size >= 10) {
                    val startMs = parseTimestamp(parts[1])
                    val endMs = parseTimestamp(parts[2])
                    val styleName = parts[3].trim()
                    val rawText = parts[9]

                    val assignedStyle = styles[styleName] ?: defaultStyle

                    // Extract {\pos(x,y)} coordinates
                    var posX: Float? = null
                    var posY: Float? = null
                    val posMatcher = POS_REGEX.matcher(rawText)
                    if (posMatcher.find()) {
                        posX = posMatcher.group(1)?.toFloatOrNull()
                        posY = posMatcher.group(2)?.toFloatOrNull()
                    }

                    // Extract override color {\c&HBBGGRR&}
                    var overrideColor: Color? = null
                    val colorMatcher = COLOR_REGEX.matcher(rawText)
                    if (colorMatcher.find()) {
                        val hex = colorMatcher.group(1)
                        if (hex != null && hex.length == 6) {
                            // ASS uses BGR format
                            val b = hex.substring(0, 2).toInt(16)
                            val g = hex.substring(2, 4).toInt(16)
                            val r = hex.substring(4, 6).toInt(16)
                            overrideColor = Color(r, g, b)
                        }
                    }

                    // Strip formatting tags and newlines
                    val cleanText = TAGS_REGEX.matcher(rawText).replaceAll("")
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .trim()

                    if (cleanText.isNotEmpty()) {
                        cues.add(
                            AssCue(
                                startMs = startMs,
                                endMs = endMs,
                                text = cleanText,
                                style = assignedStyle,
                                alignment = assignedStyle.alignment,
                                posX = posX,
                                posY = posY,
                                overrideColor = overrideColor
                            )
                        )
                    }
                }
            }
        }

        return cues.sortedBy { it.startMs }
    }
}
