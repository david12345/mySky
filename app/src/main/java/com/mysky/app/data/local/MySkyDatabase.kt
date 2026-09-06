package com.mysky.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mysky.app.data.local.entity.SightingEntity

@Database(
    entities = [SightingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MySkyDatabase : RoomDatabase() {
    abstract fun sightingDao(): SightingDao

    companion object {
        const val NAME = "mysky.db"
    }
}
