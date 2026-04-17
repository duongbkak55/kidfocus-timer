package com.kidfocus.timer.data.di

import android.content.Context
import androidx.room.Room
import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Room database and DAO dependencies.
 *
 * DataStore and Repository bindings are handled via @Inject constructors
 * annotated with @Singleton — no explicit @Provides needed for those.
 */
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
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideSessionDao(database: SessionDatabase): SessionDao =
        database.sessionDao()
}
