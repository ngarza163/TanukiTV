package com.darknet.animetv.subtitles

import com.darknet.animetv.player.subtitles.AssSubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AssSubtitleParserTest {

    @Test
    fun testParseTimestamp() {
        // "0:01:23.45" -> 1*60_000 + 23*1000 + 450 = 83450ms
        val ms = AssSubtitleParser.parseTimestamp("0:01:23.45")
        assertEquals(83450L, ms)

        val zero = AssSubtitleParser.parseTimestamp("0:00:00.00")
        assertEquals(0L, zero)
    }

    @Test
    fun testParseAssScriptWithStylesAndPositioning() {
        val script = """
            [Script Info]
            Title: Anime FanSub Episode 1
            ScriptType: v4.00+
            
            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,28,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1
            Style: Sign,Trebuchet MS,24,&H00FFFF00,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,1,0,7,10,10,10,1
            
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:05.10,0:00:08.50,Default,,0,0,0,,I didn't expect to meet an elf here.
            Dialogue: 0,0:00:12.00,0:00:16.00,Sign,,0,0,0,,{\pos(320,180)\c&H00FFFF&}Central Magic Association
        """.trimIndent()

        val cues = AssSubtitleParser.parse(script)
        assertEquals(2, cues.size)

        val dialogueCue = cues[0]
        assertEquals("I didn't expect to meet an elf here.", dialogueCue.text)
        assertEquals(5100L, dialogueCue.startMs)
        assertEquals(8500L, dialogueCue.endMs)
        assertEquals("Default", dialogueCue.style.name)

        val signCue = cues[1]
        assertEquals("Central Magic Association", signCue.text)
        assertEquals(320f, signCue.posX)
        assertEquals(180f, signCue.posY)
        assertNotNull(signCue.overrideColor)
    }
}
