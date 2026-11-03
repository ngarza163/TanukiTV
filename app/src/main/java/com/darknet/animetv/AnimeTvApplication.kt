package com.darknet.animetv

import android.app.Application
import android.content.Context
import com.darknet.animetv.data.storage.AppStorage

/**
 * Main Application class for AnimeTV Android TV rebuild.
 * Initializes local SQLite storage and provides application-wide access
 * to storage and repository services.
 */
class AnimeTvApplication : Application() {

    lateinit var storage: AppStorage
        private set

    @Volatile
    private var repositoryInstance: Any? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        storage = AppStorage(applicationContext)
    }

    companion object {
        @Volatile
        private var instance: AnimeTvApplication? = null

        fun getInstance(): AnimeTvApplication {
            return instance ?: throw IllegalStateException("AnimeTvApplication has not been initialized")
        }

        val appStorage: AppStorage
            get() = getInstance().storage

        val appContext: Context
            get() = getInstance().applicationContext

        @Suppress("UNCHECKED_CAST")
        fun <T> getRepository(): T? = getInstance().repositoryInstance as? T

        fun setRepository(repo: Any) {
            getInstance().repositoryInstance = repo
        }
    }
}
