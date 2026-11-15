package com.ming.mchat.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ming.mchat.core.data.db.MChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MChatDatabase =
        androidx.room.Room.databaseBuilder(context, MChatDatabase::class.java, "mchat.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideConversationDao(db: MChatDatabase) = db.conversationDao()
    @Provides fun provideMessageDao(db: MChatDatabase) = db.messageDao()
    @Provides fun provideAttachmentDao(db: MChatDatabase) = db.attachmentDao()
    @Provides fun provideProviderDao(db: MChatDatabase) = db.providerDao()
    @Provides fun provideModelDao(db: MChatDatabase) = db.modelDao()
    @Provides fun providePresetDao(db: MChatDatabase) = db.presetDao()
    @Provides fun provideUsageDao(db: MChatDatabase) = db.usageDao()
    @Provides fun provideAuxUsageDao(db: MChatDatabase) = db.auxUsageDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        com.ming.mchat.core.data.datastore.settingsDataStore(context)

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
