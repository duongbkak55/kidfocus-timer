package com.kidfocus.timer.data.di

import android.content.Context
import androidx.room.Room
import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionDatabase
import com.kidfocus.timer.data.database.RoutineDao
import com.kidfocus.timer.data.database.LearningAttemptDao
import com.kidfocus.timer.data.database.ChildProfileDao
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
    fun provideScheduleAdviser(api: com.kidfocus.timer.data.remote.ScheduleAiApi): com.kidfocus.timer.data.remote.ScheduleAdviser = api

    @Provides
    @Singleton
    fun provideScheduleParser(api: com.kidfocus.timer.data.remote.ScheduleAiApi): com.kidfocus.timer.data.remote.ScheduleParser = api

    @Provides
    @Singleton
    fun provideApplyScheduleUseCase(store: com.kidfocus.timer.data.schedule.RoomScheduleStore) =
        com.kidfocus.timer.domain.schedule.ApplyScheduleUseCase(store)


    @Provides
    @Singleton
    fun provideSessionDatabase(
        @ApplicationContext context: Context,
    ): SessionDatabase = Room.databaseBuilder(
        context,
        SessionDatabase::class.java,
        SessionDatabase.DATABASE_NAME,
    )
        .addMigrations(
            SessionDatabase.MIGRATION_1_2,
            SessionDatabase.MIGRATION_2_3,
            SessionDatabase.MIGRATION_3_4,
            SessionDatabase.MIGRATION_4_5,
        )
        .build()

    @Provides
    @Singleton
    fun provideSessionDao(database: SessionDatabase): SessionDao =
        database.sessionDao()

    @Provides
    @Singleton
    fun provideScheduledTaskDao(database: SessionDatabase): ScheduledTaskDao =
        database.scheduledTaskDao()

    @Provides
    @Singleton
    fun provideRoutineDao(database: SessionDatabase): RoutineDao =
        database.routineDao()

    @Provides
    @Singleton
    fun provideLearningAttemptDao(database: SessionDatabase): LearningAttemptDao =
        database.learningAttemptDao()

    @Provides
    @Singleton
    fun provideChildProfileDao(database: SessionDatabase): ChildProfileDao =
        database.childProfileDao()
}
