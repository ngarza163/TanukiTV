package com.darknet.animetv.search

import android.content.Intent
import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceSearchTest {

    @Test
    fun testSpeechRecognizerIntentStructure() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak anime title...")
        }

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)
        )
        assertEquals("Speak anime title...", intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT))
    }

    @Test
    fun testSpeechResultsParsing() {
        val simulatedSpokenText = arrayListOf("Frieren Beyond Journey's End", "Frieren")
        val resultIntent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, simulatedSpokenText)
        }

        val speechResults = resultIntent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        assertNotNull(speechResults)
        assertEquals(2, speechResults?.size)
        assertEquals("Frieren Beyond Journey's End", speechResults?.firstOrNull())
    }

    @Test
    fun testVoiceQuerySanitization() {
        val rawVoiceInput = "  Attack on Titan final season!  "
        val cleanQuery = rawVoiceInput.trim().replace(Regex("[!.,?]"), "")
        assertEquals("Attack on Titan final season", cleanQuery)
    }
}
