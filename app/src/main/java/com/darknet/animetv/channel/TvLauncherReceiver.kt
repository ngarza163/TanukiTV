package com.darknet.animetv.channel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import com.darknet.animetv.AnimeTvApplication
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by Android TV system to initialize home screen preview channels.
 */
class TvLauncherReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TvLauncherReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == TvContractCompat.ACTION_INITIALIZE_PROGRAMS) {
            Log.d(TAG, "Received ACTION_INITIALIZE_PROGRAMS from Android TV system")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = DefaultAnimeRepository.getInstance(AnimeTvApplication.appStorage)
                    val trending = repo.getTrending()
                    TvChannelManager.publishTrendingChannel(context, trending)
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing preview programs in receiver", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
