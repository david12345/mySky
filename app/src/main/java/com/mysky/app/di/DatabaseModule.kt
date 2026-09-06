package com.mysky.app.di

import android.content.Context
import androidx.room.Room
import com.mysky.app.data.local.MySkyDatabase
import com.mysky.app.data.local.SightingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MySkyDatabase =
        Room.databaseBuilder(context, MySkyDatabase::class.java, MySkyDatabase.NAME).build()

    @Provides
    fun provideSightingDao(database: MySkyDatabase): SightingDao = database.sightingDao()
}
