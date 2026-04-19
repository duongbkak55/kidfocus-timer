package com.kidfocus.timer.data.di

import android.content.Context
import androidx.room.Room
import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSessionDatabase(
        @ApplicationContext context: Context,
    ): SessionDatabase = Room.databaseBuilder(
        context,
        SessionDatabase::class.java,
        SessionDatabase.DATABASE_NAME,
    )
        .addMigrations(SessionDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideSessionDao(database: SessionDatabase): SessionDao =
        database.sessionDao()

    @Provides
    @Singleton
    fun provideScheduledTaskDao(database: SessionDatabase): ScheduledTaskDao =
        database.scheduledTaskDao()
}
