package com.darknet.animetv.sync

import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AniListSyncManagerTest {

    @Test
    fun testLoginWithDemoToken() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        val result = AniListSyncManager.loginWithToken("demo_token_12345", storage)

        // Remote call will fail on network in test env, but storage methods should exist and function
        assertFalse(result.isSuccess)
    }

    @Test
    fun testScrobbleProgressWithDemoTokenReturnsTrue() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken("demo_token_12345")

        val success = AniListSyncManager.scrobbleProgress(
            mediaIdStr = "123456",
            episodeNumber = 3,
            totalEpisodes = 12,
            storage = storage,
            animeTitle = "Frieren"
        )
        assertTrue(success)
    }

    @Test
    fun testScrobbleProgressWithoutTokenReturnsFalse() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken(null)

        val success = AniListSyncManager.scrobbleProgress(
            mediaIdStr = "123456",
            episodeNumber = 3,
            totalEpisodes = 12,
            storage = storage,
            animeTitle = "Frieren"
        )
        assertFalse(success)
    }

    @Test
    fun testDemoSyncWatchlistPopulatesStorage() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken("demo_user_token")
        storage.saveAniListUser(
            AniListUser(
                id = 999,
                name = "AnimeFanTV",
                avatarUrl = "",
                totalAnime = 5,
                episodesWatched = 42
            )
        )

        val syncRes = AniListSyncManager.syncAniListWatchlist(storage)
        assertTrue(syncRes.isSuccess)
        assertTrue(syncRes.syncedCount > 0)
    }
}
