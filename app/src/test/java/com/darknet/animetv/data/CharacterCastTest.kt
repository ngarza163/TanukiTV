package com.darknet.animetv.data

import com.darknet.animetv.data.repository.DefaultAnimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCastTest {

    private val repository = DefaultAnimeRepository()

    @Test
    fun testGetCharacterCastForAttackOnTitan() = runBlocking {
        val cast = repository.getCharacterCast("16498", "Attack on Titan")
        assertTrue(cast.isNotEmpty())
        assertEquals("Eren Yeager", cast[0].characterName)
        assertEquals("Yūki Kaji", cast[0].voiceActorName)
        assertEquals("MAIN", cast[0].characterRole)

        val mikasa = cast.firstOrNull { c -> c.characterName == "Mikasa Ackerman" }
        assertTrue(mikasa != null)
        assertEquals("Yui Ishikawa", mikasa?.voiceActorName)
    }

    @Test
    fun testGetCharacterCastForDemonSlayer() = runBlocking {
        val cast = repository.getCharacterCast("101922", "Demon Slayer: Kimetsu no Yaiba")
        assertTrue(cast.isNotEmpty())
        assertEquals("Tanjiro Kamado", cast[0].characterName)
        assertEquals("Natsuki Hanae", cast[0].voiceActorName)
        assertEquals("MAIN", cast[0].characterRole)

        val nezuko = cast.firstOrNull { c -> c.characterName == "Nezuko Kamado" }
        assertTrue(nezuko != null)
        assertEquals("Akari Kitō", nezuko?.voiceActorName)
    }

    @Test
    fun testGetCharacterCastForSoloLeveling() = runBlocking {
        val cast = repository.getCharacterCast("151807", "Solo Leveling")
        assertTrue(cast.isNotEmpty())
        assertEquals("Sung Jin-woo", cast[0].characterName)
        assertEquals("Taito Ban", cast[0].voiceActorName)
    }

    @Test
    fun testGetCharacterCastFallback() = runBlocking {
        val cast = repository.getCharacterCast("99999", "Unknown Indie Anime")
        assertTrue(cast.size >= 2)
        assertTrue(cast[0].characterName.contains("Protagonist"))
        assertEquals("MAIN", cast[0].characterRole)
        assertFalse(cast[0].voiceActorName.isBlank())
    }
}
