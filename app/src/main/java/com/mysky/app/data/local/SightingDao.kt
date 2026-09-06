package com.mysky.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mysky.app.data.local.entity.SightingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {

    @Query("SELECT * FROM sightings ORDER BY observedAtEpochSeconds DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE icao24 = :icao24 ORDER BY observedAtEpochSeconds DESC")
    fun observeTrackFor(icao24: String): Flow<List<SightingEntity>>

    @Query(
        "SELECT COUNT(*) FROM sightings " +
            "WHERE icao24 = :icao24 AND notified = 1 AND observedAtEpochSeconds >= :sinceEpochSeconds",
    )
    suspend fun countNotifiedSince(icao24: String, sinceEpochSeconds: Long): Int

    @Insert
    suspend fun insert(sighting: SightingEntity): Long

    /** Retenção: o histórico não deve crescer indefinidamente no dispositivo. */
    @Query("DELETE FROM sightings WHERE observedAtEpochSeconds < :beforeEpochSeconds")
    suspend fun deleteOlderThan(beforeEpochSeconds: Long): Int
}
