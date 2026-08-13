package com.callbackdev.tweather.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One weather fetch persisted as a git-style "commit" for the Logs screen
 * (`weather_history.diff`): short hash, fixed author, timestamp and a flattened
 * key→value snapshot (JSON) that Fase 8 diffs against the previous entry.
 */
@Entity(tableName = "weather_history")
data class WeatherHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "city_key") val cityKey: String,
    @ColumnInfo(name = "city_label") val cityLabel: String,
    val hash: String,
    val author: String,
    @ColumnInfo(name = "timestamp_epoch_s") val timestampEpochSeconds: Long,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String
)

@Dao
interface WeatherHistoryDao {

    @Insert
    suspend fun insert(entry: WeatherHistoryEntry): Long

    @Query(
        "SELECT * FROM weather_history WHERE city_key = :cityKey " +
            "ORDER BY timestamp_epoch_s DESC LIMIT :limit"
    )
    suspend fun historyFor(cityKey: String, limit: Int): List<WeatherHistoryEntry>

    @Query("SELECT * FROM weather_history ORDER BY timestamp_epoch_s DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<WeatherHistoryEntry>>

    @Query(
        "DELETE FROM weather_history WHERE id NOT IN " +
            "(SELECT id FROM weather_history ORDER BY timestamp_epoch_s DESC LIMIT :keep)"
    )
    suspend fun prune(keep: Int)
}

@Database(entities = [WeatherHistoryEntry::class], version = 1, exportSchema = false)
abstract class TweatherDatabase : RoomDatabase() {
    abstract fun weatherHistoryDao(): WeatherHistoryDao
}
