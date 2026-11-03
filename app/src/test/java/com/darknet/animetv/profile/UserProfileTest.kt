package com.darknet.animetv.profile

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.UserAccount
import com.darknet.animetv.data.model.UserProfile
import com.darknet.animetv.data.storage.AppStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserProfileTest {

    @Test
    fun testKidsProfileFiltersMatureContent() {
        val kidsProfile = UserProfile(
            id = "profile_kids",
            name = "Kids Safe",
            isKidsMode = true
        )

        val safeAnime = Anime(
            id = "pokemon",
            title = "Pokemon Horizons",
            genres = listOf("Action", "Adventure", "Fantasy"),
            synopsis = "Exciting journey with pocket monsters"
        )
        assertTrue(kidsProfile.isAnimeAllowed(safeAnime))

        val matureAnime = Anime(
            id = "berserk",
            title = "Berserk",
            genres = listOf("Action", "Horror", "Gore"),
            synopsis = "Dark fantasy with extreme violence"
        )
        assertFalse(kidsProfile.isAnimeAllowed(matureAnime))
    }

    @Test
    fun testAdultProfileAllowsAllContent() {
        val mainProfile = UserProfile(
            id = "profile_main",
            name = "Main",
            isKidsMode = false
        )

        val matureAnime = Anime(
            id = "tokyo-ghoul",
            title = "Tokyo Ghoul",
            genres = listOf("Action", "Horror", "Psychological"),
            synopsis = "Ghouls eating humans"
        )
        assertTrue(mainProfile.isAnimeAllowed(matureAnime))
    }

    @Test
    fun testAppStorageProfilesPersistence() {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        val defaultProfiles = storage.getProfiles()
        assertTrue(defaultProfiles.size >= 2)

        val customProfile = UserProfile(
            id = "profile_guest",
            name = "Guest Viewer",
            isKidsMode = false,
            isPinLocked = true,
            pinHash = "1234"
        )
        storage.saveProfile(customProfile)

        val updated = storage.getProfiles()
        val found = updated.find { it.id == "profile_guest" }
        assertNotNull(found)
        assertEquals("Guest Viewer", found?.name)
        assertTrue(found?.isPinLocked == true)
        assertEquals("1234", found?.pinHash)
    }

    @Test
    fun testProfileCustomizationPersistence() {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        val customProfile = UserProfile(
            id = "profile_senpai",
            name = "Chopper Senpai",
            avatarColor = 0xFFE11D48L,
            avatarIcon = "🦌",
            avatarUrl = "https://cdn.myanimelist.net/images/characters/3/100534.jpg",
            backgroundTheme = "Twilight Comet",
            bgImageUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401",
            bgGradientStart = 0xFF1E1B4BL,
            bgGradientEnd = 0xFF0C0D14L,
            isKidsMode = false
        )
        storage.saveProfile(customProfile)

        val retrieved = storage.getProfiles().find { it.id == "profile_senpai" }
        assertNotNull(retrieved)
        assertEquals("Chopper Senpai", retrieved?.name)
        assertEquals("🦌", retrieved?.avatarIcon)
        assertEquals("https://cdn.myanimelist.net/images/characters/3/100534.jpg", retrieved?.avatarUrl)
        assertEquals(0xFFE11D48L, retrieved?.avatarColor)
        assertEquals("Twilight Comet", retrieved?.backgroundTheme)
        assertEquals("https://images.unsplash.com/photo-1534447677768-be436bb09401", retrieved?.bgImageUrl)
        assertEquals(0xFF1E1B4BL, retrieved?.bgGradientStart)
        assertEquals(0xFF0C0D14L, retrieved?.bgGradientEnd)
    }

    @Test
    fun testUserAccountPersistenceAndLogout() {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        assertNull(storage.getUserAccount())

        val account = UserAccount(
            email = "vip@tanukitv.com",
            username = "TanukiVIP",
            membershipTier = "TanukiTV VIP",
            syncEnabled = true
        )
        storage.saveUserAccount(account)

        val saved = storage.getUserAccount()
        assertNotNull(saved)
        assertEquals("vip@tanukitv.com", saved?.email)
        assertEquals("TanukiVIP", saved?.username)
        assertEquals("TanukiTV VIP", saved?.membershipTier)
        assertTrue(saved?.isLoggedIn == true)
        assertTrue(saved?.syncEnabled == true)

        storage.clearUserAccount()
        assertNull(storage.getUserAccount())
    }
}
