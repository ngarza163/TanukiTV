package com.darknet.animetv.player

import com.darknet.animetv.player.aniskip.AniSkipClient
import com.darknet.animetv.player.aniskip.SkipInterval
import com.darknet.animetv.player.aniskip.SkipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSkipTest {

    private val client = AniSkipClient()

    @Test
    fun testParseAniSkipJsonResponse() {
        val json = """
            {
                "found": true,
                "results": [
                    {
                        "interval": {
                            "startTime": 90.0,
                            "endTime": 175.5
                        },
                        "skipType": "op",
                        "skipId": "op-101"
                    },
                    {
                        "interval": {
                            "startTime": 1320.0,
                            "endTime": 1410.0
                        },
                        "skipType": "ed",
                        "skipId": "ed-102"
                    }
                ],
                "message": "Successfully found skip intervals",
                "statusCode": 200
            }
        """.trimIndent()

        val parsed = client.parseAniSkipJson(json)
        assertEquals(2, parsed.size)

        val op = parsed[0]
        assertEquals(90_000L, op.startMs)
        assertEquals(175_500L, op.endMs)
        assertEquals(SkipType.OP, op.type)
        assertTrue(op.isOpening)
        assertFalse(op.isEnding)
        assertEquals(85, op.durationSeconds)

        val ed = parsed[1]
        assertEquals(1320_000L, ed.startMs)
        assertEquals(1410_000L, ed.endMs)
        assertEquals(SkipType.ED, ed.type)
        assertTrue(ed.isEnding)
        assertFalse(ed.isOpening)
        assertEquals(90, ed.durationSeconds)
    }

    @Test
    fun testParseEmptyOrNotFoundResponse() {
        val jsonNotFound = """{"found": false, "results": [], "statusCode": 404}"""
        val parsed = client.parseAniSkipJson(jsonNotFound)
        assertTrue(parsed.isEmpty())

        val invalidJson = "invalid response"
        val parsedInvalid = client.parseAniSkipJson(invalidJson)
        assertTrue(parsedInvalid.isEmpty())
    }

    @Test
    fun testSynthesizeDefaultIntervals() {
        val durationMs = 1_440_000L // 24 minutes
        val synthesized = client.synthesizeDefaultIntervals(durationMs)
        assertEquals(2, synthesized.size)

        val op = synthesized[0]
        assertTrue(op.isOpening)
        assertEquals(90_000L, op.startMs)
        assertEquals(175_000L, op.endMs)

        val ed = synthesized[1]
        assertTrue(ed.isEnding)
        assertEquals(durationMs - 120_000L, ed.startMs)
        assertEquals(durationMs - 30_000L, ed.endMs)
    }

    @Test
    fun testSkipIntervalHelperProperties() {
        val recap = SkipInterval(startMs = 0L, endMs = 60_000L, type = SkipType.RECAP)
        assertTrue(recap.isRecap)
        assertFalse(recap.isOpening)
        assertFalse(recap.isEnding)
        assertEquals(60_000L, recap.durationMs)
        assertEquals(60, recap.durationSeconds)
    }
}
