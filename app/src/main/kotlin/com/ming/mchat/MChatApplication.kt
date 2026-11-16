package com.ming.mchat

import android.app.Application
import com.ming.mchat.core.attachment.AttachmentInitializer
import com.ming.mchat.core.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MChatApplication : Application() {

    @Inject lateinit var attachmentInitializer: AttachmentInitializer
    @Inject lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        attachmentInitializer.init()
        databaseSeeder.seedAsync()
    }
}
