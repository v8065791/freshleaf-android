package dev.freshleaf.reader

import android.app.Application
import dev.freshleaf.reader.data.AppDatabase
import dev.freshleaf.reader.data.FreshLeafRepository
import dev.freshleaf.reader.data.FreshLeafApplicationAccessor
import dev.freshleaf.reader.data.FreshRssApi
import dev.freshleaf.reader.data.SecureCredentials
import dev.freshleaf.reader.data.SyncScheduler
import dev.freshleaf.reader.data.UserPreferences

class FreshLeafApplication : Application(), FreshLeafApplicationAccessor {
    override val repository: FreshLeafRepository by lazy {
        FreshLeafRepository(AppDatabase.create(this), SecureCredentials(this), FreshRssApi())
    }
    val userPreferences: UserPreferences by lazy { UserPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedule(this)
    }
}
